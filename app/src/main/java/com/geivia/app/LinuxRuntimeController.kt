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
        check(activeSession == null) { "Já existe uma sessão Linux em execução." }
        val session = launcher.runCommand(args)
            ?: throw IllegalStateException("O motor Linux não conseguiu iniciar o comando.")
        activeSession = session
        try {
            readUntilClosed(session, onOutput)
            awaitExitCode(session)
        } finally {
            session.close()
            activeSession = null
        }
    }

    fun startDebianShell(): ProotLauncher.Session {
        check(activeSession == null) { "Já existe uma sessão Linux em execução." }
        val session = launcher.startSession("debian")
            ?: throw IllegalStateException(
                "Debian ainda não está instalado. Use primeiro Preparar Debian."
            )
        activeSession = session
        return session
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
            if (activeSession === session) activeSession = null
        }
    }

    fun send(session: ProotLauncher.Session, text: String) {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        check(session.write(bytes) == bytes.size) { "Não foi possível enviar o comando ao terminal Linux." }
    }

    fun cancel() {
        activeSession?.close()
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
