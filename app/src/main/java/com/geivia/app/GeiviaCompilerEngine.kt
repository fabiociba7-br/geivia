package com.geivia.app

import java.io.File

/**
 * GEIVIA's internal Android compiler facade.
 *
 * The UI talks only to this component. The current backend runs Gradle, JDK and
 * Android SDK inside GEIVIA's private Debian/PRoot runtime; no external IDE app
 * or Code on the Go intent is used. Backend internals can evolve independently.
 */
internal object GeiviaCompilerEngine {
    const val ENGINE_NAME = "GEIVIA Android Compiler"

    fun installToolchain(): List<String> = GeiviaAndroidBuild.installToolchain()

    fun compileDebug(projectRoot: File): List<String> {
        require(projectRoot.exists() && projectRoot.isDirectory) {
            "A pasta do projeto não existe ou não está acessível."
        }
        val canonicalRoot = projectRoot.canonicalFile
        val hasGradle = File(canonicalRoot, "settings.gradle").isFile ||
            File(canonicalRoot, "settings.gradle.kts").isFile ||
            File(canonicalRoot, "build.gradle").isFile ||
            File(canonicalRoot, "build.gradle.kts").isFile ||
            File(canonicalRoot, "app/build.gradle").isFile ||
            File(canonicalRoot, "app/build.gradle.kts").isFile
        require(hasGradle) { "Projeto Android/Gradle não reconhecido: nenhum arquivo Gradle foi encontrado." }
        return GeiviaAndroidBuild.buildCommand(canonicalRoot.absolutePath)
    }

    fun locateGeneratedApk(projectRoot: File): File? =
        GeiviaAndroidBuild.findApk(projectRoot.canonicalFile)
}
