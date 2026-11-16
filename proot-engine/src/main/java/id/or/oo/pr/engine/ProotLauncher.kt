package id.or.oo.pr.engine

import android.util.Log
import java.io.File
import kotlin.concurrent.thread

internal fun commandArgs(prefixDir: File, args: List<String>): List<String> =
    listOf(File(prefixDir, "bin/pr-cli").absolutePath) + args

class ProotLauncher(private val host: ProotHost) {
    companion object { private const val TAG = "PR" }
    val prefixDir: File get() = host.prefixDir

    fun startSession(distroName: String, user: String = "root", isolated: Boolean = false, rows: Int = 50, cols: Int = 200): Session? {
        val prCli = File(prefixDir, "bin/pr-cli")
        if (!prCli.exists()) { Log.e(TAG, "pr-cli not found at $prCli"); return null }
        val envVars = buildEnvVars()
        val args = arrayOf(prCli.absolutePath, "login", distroName, "--user", user)
        val (masterFd, pid) = PtyNative.forkPtyWithPid(args[0], args, envVars, rows, cols)
        if (masterFd < 0) { Log.e(TAG, "forkPty failed with fd=$masterFd"); return null }
        Log.i(TAG, "PTY session started for $distroName, masterFd=$masterFd")
        return Session(masterFd, pid)
    }

    fun startCustomSession(args: List<String>, rows: Int = 24, cols: Int = 80): Session? {
        require(args.isNotEmpty()) { "Command arguments cannot be empty" }
        val envVars = buildEnvVars()
        val (masterFd, pid) = PtyNative.forkPtyWithPid(args[0], args.toTypedArray(), envVars, rows, cols)
        if (masterFd < 0) { Log.e(TAG, "forkPty failed with fd=$masterFd for ${args.joinToString(" ")}"); return null }
        Log.i(TAG, "PTY custom session started: ${args.joinToString(" ")}, masterFd=$masterFd")
        return Session(masterFd, pid)
    }

    fun runCommand(args: List<String>, rows: Int = 24, cols: Int = 80): Session? =
        startCustomSession(commandArgs(prefixDir, args), rows, cols)

    private fun buildEnvVars(): Array<String> {
        val binDir = File(prefixDir, "bin")
        val homeDir = host.homeDir
        val prefix = prefixDir.absolutePath
        val loader = File(host.nativeLibraryDir, "libproot-loader.so")
        check(loader.isFile) { "libproot-loader.so não está disponível em ${host.nativeLibraryDir}" }
        return arrayOf(
            "APP_PREFIX", prefix,
            "APP_HOME", homeDir.absolutePath,
            "APP_PACKAGE", host.packageName,
            "PATH", "${binDir.absolutePath}:/system/bin:/system/xbin",
            "HOME", homeDir.absolutePath,
            "PROOT_NO_SECCOMP", "1",
            "PROOT_LOADER", loader.absolutePath,
            "PROOT_TMP_DIR", host.cacheDir.absolutePath,
            "TERM", "xterm-256color",
            "LANG", "en_US.UTF-8",
            "TMPDIR", host.cacheDir.absolutePath,
            "APP_DNS_SERVERS", host.dnsServers.joinToString(","),
        )
    }

    class Session(val masterFd: Int, val pid: Int = -1) {
        @Volatile var closed = false
            private set
        @Volatile var exitCode: Int? = null
            private set
        init {
            if (pid > 0) thread(isDaemon = true, name = "pty-reaper-$pid") { exitCode = PtyNative.waitPidBlocking(pid) }
        }
        fun read(buf: ByteArray, offset: Int = 0, length: Int = buf.size): Int {
            if (closed) return -1
            return PtyNative.read(masterFd, buf, offset, length)
        }
        fun write(data: ByteArray): Int { if (closed) return -1; return PtyNative.write(masterFd, data, 0, data.size) }
        fun resize(rows: Int, cols: Int): Int { if (closed) return -1; return PtyNative.resize(masterFd, rows, cols) }
        @Synchronized fun close() {
            if (!closed) {
                closed = true
                if (pid > 0) { try { PtyNative.kill(pid) } catch (_: Exception) {} }
                try { PtyNative.close(masterFd) } catch (_: Exception) {}
            }
        }
    }
}
