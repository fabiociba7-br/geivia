package com.geivia.app

import android.content.Context
import android.os.Build
import id.or.oo.pr.engine.ProotHost
import id.or.oo.pr.engine.ProotLauncher
import java.io.File
import java.util.zip.ZipFile

/**
 * Wires the pinned, packaged PRoot engine into GEIVIA's private Linux prefix.
 * Executable PRoot payloads are staged from the APK into app-private storage,
 * avoiding reliance on Android's nativeLibraryDir extraction behavior.
 */
internal class GeiviaProotHost(context: Context) : ProotHost {
    private val appContext = context.applicationContext

    override val prefixDir: File = File(appContext.filesDir, "linux/usr")
    override val homeDir: File = File(appContext.filesDir, "linux/home")
    override val packageName: String = appContext.packageName
    override val cacheDir: File = File(appContext.cacheDir, "linux-runtime")
    override val nativeLibraryDir: File = File(appContext.filesDir, "linux/native-libs")

    fun prepare(): ProotLauncher {
        listOf(prefixDir, homeDir, cacheDir, File(prefixDir, "bin"), nativeLibraryDir).forEach { directory ->
            check(directory.mkdirs() || directory.isDirectory) {
                "Não foi possível preparar " + directory.name + " para o ambiente Linux."
            }
        }

        stageExecutableLibraries()
        linkNativeBinary("libpr-cli.so", File(prefixDir, "bin/pr-cli"))
        linkNativeBinary("libproot.so", File(prefixDir, "bin/proot"))
        return ProotLauncher(this)
    }

    /**
     * The APK contains these ARM64 ELF executables as jniLibs. Copying them to
     * filesDir makes their presence and executable permissions deterministic on
     * devices whose PackageManager native-library extraction differs.
     */
    private fun stageExecutableLibraries() {
        check(Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }) {
            "O ambiente Linux do GEIVIA exige suporte ARM64 (arm64-v8a)."
        }
        val names = listOf("libpr-cli.so", "libproot.so", "libproot-loader.so")
        ZipFile(appContext.applicationInfo.sourceDir).use { apk ->
            for (name in names) {
                val entryName = "lib/arm64-v8a/$name"
                val entry = apk.getEntry(entryName)
                    ?: throw IllegalStateException(
                        "O APK instalado não contém $name para ARM64. Reinstale uma versão completa do GEIVIA."
                    )
                val destination = File(nativeLibraryDir, name)
                val shouldCopy = !destination.isFile || destination.length() != entry.size
                if (shouldCopy) {
                    apk.getInputStream(entry).use { input ->
                        destination.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                check(destination.setExecutable(true, true) || destination.canExecute()) {
                    "O Android não permitiu preparar $name para execução no espaço privado do GEIVIA."
                }
                check(destination.length() == entry.size) {
                    "A cópia de $name ficou incompleta. Verifique o armazenamento e reinstale o GEIVIA."
                }
            }
        }
    }

    private fun linkNativeBinary(libraryName: String, destination: File) {
        val source = File(nativeLibraryDir, libraryName)
        check(source.isFile && source.canExecute()) {
            "Componente nativo $libraryName não pôde ser preparado no armazenamento privado do GEIVIA."
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
