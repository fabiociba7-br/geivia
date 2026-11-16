package com.geivia.app

import android.content.Context
import android.os.Build
import id.or.oo.pr.engine.ProotHost
import id.or.oo.pr.engine.ProotLauncher
import java.io.File
import java.util.zip.ZipFile

/**
 * Uses Android's extracted native-library directory for PRoot executables.
 * filesDir/cacheDir can be mounted noexec (observed on Android/Xiaomi).
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
        verifyExtractedNativeLibraries()
        linkNativeBinary("libpr-cli.so", File(prefixDir, "bin/pr-cli"))
        linkNativeBinary("libproot.so", File(prefixDir, "bin/proot"))
        return ProotLauncher(this)
    }

    /** Confirms APK payloads are extracted into Android's executable native directory. */
    private fun verifyExtractedNativeLibraries() {
        check(Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }) {
            "O ambiente Linux do GEIVIA exige suporte ARM64 (arm64-v8a)."
        }
        val names = listOf("libpr-cli.so", "libproot.so", "libproot-loader.so")
        ZipFile(appContext.applicationInfo.sourceDir).use { apk ->
            for (name in names) {
                val entry = apk.getEntry("lib/arm64-v8a/$name")
                    ?: throw IllegalStateException("O APK instalado não contém $name para ARM64.")
                val extracted = File(nativeLibraryDir, name)
                check(extracted.isFile && extracted.length() == entry.size) {
                    "O Android não extraiu $name para a pasta nativa executável. Reinstale o GEIVIA com os componentes ARM64 completos."
                }
                check(extracted.canExecute()) {
                    "O Android encontrou $name, mas não permite executá-lo em " + nativeLibraryDir.absolutePath + "."
                }
            }
        }
    }

    private fun linkNativeBinary(libraryName: String, destination: File) {
        val source = File(nativeLibraryDir, libraryName)
        check(source.isFile && source.canExecute()) {
            "Componente nativo $libraryName não está disponível para execução em " + nativeLibraryDir.absolutePath + "."
        }
        val sourcePath = source.toPath()
        val destinationPath = destination.toPath()
        if (java.nio.file.Files.isSymbolicLink(destinationPath)) {
            if (runCatching { java.nio.file.Files.readSymbolicLink(destinationPath) == sourcePath }.getOrDefault(false)) return
            java.nio.file.Files.delete(destinationPath)
        } else if (destination.exists()) {
            check(destination.canonicalFile == source.canonicalFile) {
                "O destino " + destination.name + " já existe e não é o componente nativo esperado."
            }
            return
        }
        java.nio.file.Files.createSymbolicLink(destinationPath, sourcePath)
    }
}
