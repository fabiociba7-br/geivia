package com.geivia.app

import android.content.Context
import id.or.oo.pr.engine.ProotLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Executes real PRoot commands through the native engine packaged in the APK.
 * It never invokes Android's shell and never grants root privileges.
 */
internal class LinuxRuntimeController(context: Context) {
    private val host = GeiviaProotHost(context.applicationContext)
    private val launcher: ProotLauncher by lazy { host.prepare() }

    @Volatile
    private var activeSession: ProotLauncher.Session? = null

    val prefixDir: File get() = host.prefixDir

    fun prepareEngine() {
        launcher
    }

    suspend fun execute(
        args: List<String>,
        onOutput: (String) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val session = openCommandSession(args)
        try {
            readUntilClosed(session, onOutput)
            awaitExitCode(session)
        } finally {
            session.close()
            releaseSession(session)
        }
    }

    /**
     * Installs Debian, runs the upstream distro smoke suite, then executes a real
     * command inside the guest. A non-zero stage stops the sequence immediately.
     */
    suspend fun installAndVerifyDebian(onOutput: (String) -> Unit): List<Int> {
        val results = mutableListOf<Int>()
        val stages = listOf(
            "Instalando Debian ARM64" to LinuxRuntimeCommands.installDebian(),
            "Validando a distribuição e o PRoot" to LinuxRuntimeCommands.testDebianBase(),
            "Testando shell, diretórios e execução de comandos" to LinuxRuntimeCommands.smokeCheckDebian()
        )
        for ((label, args) in stages) {
            onOutput("\n[GEIVIA] $label…\n")
            val code = execute(args, onOutput)
            results += code
            onOutput("\n[GEIVIA] Etapa concluída com código $code.\n")
            check(code == 0) {
                "$label falhou (código $code). Consulte a saída acima; a próxima etapa não foi executada."
            }
        }
        onOutput("\n[GEIVIA] Debian instalado e verificado. Procure o marcador GEIVIA_DEBIAN_SHELL_OK acima.\n")
        return results
    }

    fun startDebianShell(): ProotLauncher.Session = synchronized(this) {
        check(activeSession == null) { "Já existe uma sessão Linux em execução." }
        val session = launcher.startSession("debian")
            ?: throw IllegalStateException(
                "Debian ainda não está instalado. Use primeiro Preparar Debian."
            )
        activeSession = session
        session
    }

    suspend fun readSession(
        session: ProotLauncher.Session,
        onOutput: (String) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        try {
            readUntilClosed(session, onOutput)
            awaitExitCode(session)
        } finally {
            session.close()
            releaseSession(session)
        }
    }

    fun send(session: ProotLauncher.Session, text: String) {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        check(session.write(bytes) == bytes.size) { "Não foi possível enviar o comando ao terminal Linux." }
    }

    fun cancel() {
        activeSession?.close()
    }

    @Synchronized
    private fun openCommandSession(args: List<String>): ProotLauncher.Session {
        check(activeSession == null) { "Já existe uma sessão Linux em execução." }
        val session = launcher.runCommand(args)
            ?: throw IllegalStateException("O motor Linux não conseguiu iniciar o comando.")
        activeSession = session
        return session
    }

    @Synchronized
    private fun releaseSession(session: ProotLauncher.Session) {
        if (activeSession === session) activeSession = null
    }

    private fun readUntilClosed(session: ProotLauncher.Session, onOutput: (String) -> Unit) {
        val buffer = ByteArray(8192)
        while (!session.closed) {
            val count = session.read(buffer)
            when {
                count < 0 -> return
                count == 0 -> Thread.sleep(15)
                else -> onOutput(String(buffer, 0, count, StandardCharsets.UTF_8))
            }
        }
    }

    private fun awaitExitCode(session: ProotLauncher.Session): Int {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (session.exitCode == null && System.nanoTime() < deadline) {
            Thread.sleep(20)
        }
        return session.exitCode ?: -1
    }
}
