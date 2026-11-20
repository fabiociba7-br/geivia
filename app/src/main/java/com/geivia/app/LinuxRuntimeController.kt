package com.geivia.app

import android.content.Context
import id.or.oo.pr.engine.ProotLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

internal class LinuxRuntimeController(context: Context) {
    private val host = GeiviaProotHost(context.applicationContext)
    private val launcher: ProotLauncher by lazy { host.prepare() }
    @Volatile private var activeSession: ProotLauncher.Session? = null
    val prefixDir: File get() = host.prefixDir
    val homeDir: File get() = host.homeDir

    fun prepareEngine() { launcher }

    suspend fun execute(args: List<String>, timeoutMs: Long? = null, onOutput: (String) -> Unit): Int =
        withContext(Dispatchers.IO) {
            val session = openCommandSession(args)
            try {
                val timedOut = readUntilClosed(session, onOutput, timeoutMs)
                if (timedOut) {
                    onOutput("\n[GEIVIA] Tempo limite excedido (${timeoutMs} ms). Processo interrompido para evitar espera infinita.\n")
                    -124
                } else awaitExitCode(session)
            } finally {
                session.close()
                releaseSession(session)
            }
        }

    /**
     * Installs Debian and validates it with a bounded direct shell check.
     * The upstream distro suite deploys a separate test binary and can stall
     * on some Android/PRoot combinations.
     */
    suspend fun installAndVerifyDebian(onOutput: (String) -> Unit): List<Int> {
        // Reuse a healthy Debian installation instead of calling "install debian"
        // again (the installer correctly exits with code 1 when it already exists).
        onOutput("\\n[GEIVIA] Verificando se o Debian ARM64 já está instalado…\\n")
        val existingCheck = execute(LinuxRuntimeCommands.testDebianBase(), 60_000L, onOutput)
        if (existingCheck == 0) {
            onOutput("\\n[GEIVIA] Debian existente validado; instalação ignorada.\\n")
            return listOf(0)
        }
        onOutput("\\n[GEIVIA] Debian não passou na verificação inicial (código $existingCheck). Tentando preparar a distribuição…\\n")
        val installCode = execute(LinuxRuntimeCommands.installDebian(), 30 * 60 * 1000L, onOutput)
        onOutput("\\n[GEIVIA] Instalação/verificação de presença concluída com código $installCode.\\n")
        if (installCode != 0) {
            // Some PRoot installers report "already installed" as an error. Re-check
            // the guest before surfacing failure; never delete or reinstall its data.
            val outputCapture = StringBuilder()
            val verifyCode = execute(LinuxRuntimeCommands.testDebianBase(), 60_000L) { chunk ->
                outputCapture.append(chunk)
                onOutput(chunk)
            }
            if (verifyCode == 0) {
                onOutput("\\n[GEIVIA] A distribuição já existia e foi validada; prosseguindo sem reinstalar.\\n")
                return listOf(0, 0)
            }
            error("Debian já existe ou a instalação não pôde ser concluída (código $installCode); a validação do ambiente também falhou (código $verifyCode). Nenhum dado foi removido. Consulte a saída para diagnóstico.")
        }
        onOutput("\\n[GEIVIA] Verificando shell e arquivos essenciais do Debian…\\n")
        val verifyCode = execute(LinuxRuntimeCommands.testDebianBase(), 60_000L, onOutput)
        onOutput("\\n[GEIVIA] Verificação do Debian concluída com código $verifyCode.\\n")
        check(verifyCode == 0) {
            if (verifyCode == -124) "A verificação do Debian excedeu o tempo limite. Nenhum dado foi removido."
            else "O Debian foi encontrado, mas não passou na verificação básica (código $verifyCode). Nenhum dado foi removido."
        }
        onOutput("\\n[GEIVIA] Debian instalado e verificado com sucesso.\\n")
        return listOf(installCode, verifyCode)
    }

    fun startDebianShell(rows: Int = 50, cols: Int = 80): ProotLauncher.Session = synchronized(this) {
        check(activeSession == null) { "Já existe uma sessão Linux em execução." }
        val session = launcher.startSession("debian", rows = rows, cols = cols)
            ?: throw IllegalStateException("Debian ainda não está instalado. Use primeiro Preparar Debian.")
        activeSession = session
        session
    }

    suspend fun readSession(session: ProotLauncher.Session, onOutput: (String) -> Unit): Int =
        withContext(Dispatchers.IO) {
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

    fun resize(session: ProotLauncher.Session, rows: Int, cols: Int) {
        require(rows >= 8 && cols >= 20) { "Dimensões de terminal inválidas." }
        check(session.resize(rows, cols) == 0) { "Não foi possível redimensionar o terminal Linux." }
    }

    fun cancel() { activeSession?.close() }

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

    /** Native PTY reads may block; watchdog closes the PTY at the deadline. */
    private fun readUntilClosed(
        session: ProotLauncher.Session,
        onOutput: (String) -> Unit,
        timeoutMs: Long? = null
    ): Boolean {
        val timedOut = AtomicBoolean(false)
        val finished = CountDownLatch(1)
        if (timeoutMs != null) {
            require(timeoutMs > 0) { "timeoutMs precisa ser positivo." }
            thread(isDaemon = true, name = "geivia-command-timeout") {
                if (!finished.await(timeoutMs, TimeUnit.MILLISECONDS) && !session.closed) {
                    timedOut.set(true)
                    session.close()
                }
            }
        }
        try {
            val buffer = ByteArray(8192)
            while (!session.closed) {
                val count = session.read(buffer)
                when {
                    count < 0 -> return timedOut.get()
                    count == 0 -> Thread.sleep(15)
                    else -> onOutput(String(buffer, 0, count, StandardCharsets.UTF_8))
                }
            }
            return timedOut.get()
        } finally {
            finished.countDown()
        }
    }

    private fun awaitExitCode(session: ProotLauncher.Session): Int {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (session.exitCode == null && System.nanoTime() < deadline) Thread.sleep(20)
        return session.exitCode ?: -1
    }
}
