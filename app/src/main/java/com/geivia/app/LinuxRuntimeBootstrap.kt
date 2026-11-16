package com.geivia.app

import android.content.Context
import id.or.oo.pr.engine.ProotHost
import id.or.oo.pr.engine.ProotLauncher
import java.io.File
import java.nio.file.Files

/**
 * Wires the pinned, packaged PRoot engine into GEIVIA's private Linux prefix.
 * This prepares the launcher only; a distribution must still be installed by the user.
 */
internal class GeiviaProotHost(context: Context) : ProotHost {
    private val appContext = context.applicationContext

    override val prefixDir: File = File(appContext.filesDir, "linux/usr")
    override val homeDir: File = File(appContext.filesDir, "linux/home")
    override val packageName: String = appContext.packageName
    override val cacheDir: File = File(appContext.cacheDir, "linux-runtime")
    override val nativeLibraryDir: File = File(appContext.applicationInfo.nativeLibraryDir)

    fun prepare(): ProotLauncher {
        listOf(prefixDir, homeDir, cacheDir, File(prefixDir, "bin")).forEach { directory ->
            check(directory.mkdirs() || directory.isDirectory) {
                "Não foi possível preparar " + directory.name + " para o ambiente Linux."
            }
        }

        linkNativeBinary("libpr-cli.so", File(prefixDir, "bin/pr-cli"))
        linkNativeBinary("libproot.so", File(prefixDir, "bin/proot"))
        return ProotLauncher(this)
    }

    private fun linkNativeBinary(libraryName: String, destination: File) {
        val source = File(appContext.applicationInfo.nativeLibraryDir, libraryName)
        check(source.isFile) {
            "Componente nativo " + libraryName + " não está disponível neste aparelho."
        }
        val sourcePath = source.toPath()
        val destinationPath = destination.toPath()
        if (Files.isSymbolicLink(destinationPath)) {
            if (runCatching { Files.readSymbolicLink(destinationPath) == sourcePath }.getOrDefault(false)) return
            Files.delete(destinationPath)
        } else if (destination.exists()) {
            check(destination.canonicalFile == source.canonicalFile) {
                "O destino " + destination.name + " já existe e não é o componente nativo esperado."
            }
            return
        }
        Files.createSymbolicLink(destinationPath, sourcePath)
    }
}
