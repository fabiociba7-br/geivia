package id.or.oo.pr.engine

object PtyNative {
    const val STILL_RUNNING = -2
    init { System.loadLibrary("ptyjni") }
    fun forkPty(cmd: String, args: Array<String>?, envVars: Array<String>?, rows: Int, cols: Int): Int =
        forkPtyWithPid(cmd, args, envVars, rows, cols).first
    fun forkPtyWithPid(cmd: String, args: Array<String>?, envVars: Array<String>?, rows: Int, cols: Int): Pair<Int, Int> {
        val pid = IntArray(1) { -1 }
        val fd = nativeForkPty(cmd, args, envVars, rows, cols, pid)
        return fd to (if (fd >= 0) pid[0] else -1)
    }
    fun waitPidBlocking(pid: Int): Int = nativeWaitPidBlocking(pid)
    fun read(fd: Int, buf: ByteArray, offset: Int, length: Int): Int = nativeRead(fd, buf, offset, length)
    fun write(fd: Int, buf: ByteArray, offset: Int, length: Int): Int = nativeWrite(fd, buf, offset, length)
    fun resize(fd: Int, rows: Int, cols: Int): Int = nativeResize(fd, rows, cols)
    internal fun waitPid(pid: Int): Int = nativeWaitPid(pid)
    fun close(fd: Int) = nativeClose(fd)
    fun kill(pid: Int): Int = nativeKill(pid)
    private external fun nativeForkPty(cmd: String, args: Array<String>?, envVars: Array<String>?, rows: Int, cols: Int, pidOut: IntArray): Int
    private external fun nativeRead(fd: Int, buf: ByteArray, offset: Int, length: Int): Int
    private external fun nativeWrite(fd: Int, buf: ByteArray, offset: Int, length: Int): Int
    private external fun nativeResize(fd: Int, rows: Int, cols: Int): Int
    private external fun nativeWaitPid(pid: Int): Int
    private external fun nativeWaitPidBlocking(pid: Int): Int
    private external fun nativeClose(fd: Int)
    private external fun nativeKill(pid: Int): Int
}
