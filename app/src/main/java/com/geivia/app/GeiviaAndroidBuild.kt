package com.geivia.app

import java.io.File

/**
 * On-device Android build using the existing GEIVIA Debian/PRoot environment.
 * Runs the project's Gradle wrapper when available, or the installed Gradle.
 */
internal object GeiviaAndroidBuild {
    fun installToolchain(): List<String> = listOf(
        "login", "debian", "--",
        "/bin/sh", "-lc",
        """
        set -eu
        export DEBIAN_FRONTEND=noninteractive
        apt-get update
        apt-get install -y --no-install-recommends openjdk-17-jdk-headless wget unzip ca-certificates
        if ! command -v gradle >/dev/null 2>&1; then
          cd /tmp
          wget -q https://services.gradle.org/distributions/gradle-8.10.2-bin.zip
          unzip -q gradle-8.10.2-bin.zip -d /opt
          ln -sf /opt/gradle-8.10.2/bin/gradle /usr/local/bin/gradle
        fi
        ANDROID_HOME=/opt/android-sdk
        mkdir -p "${'$'}ANDROID_HOME/cmdline-tools"
        if [ ! -x "${'$'}ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
          cd /tmp
          wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdtools.zip
          unzip -q cmdtools.zip -d /tmp/cmdtools
          mkdir -p "${'$'}ANDROID_HOME/cmdline-tools/latest"
          mv /tmp/cmdtools/cmdline-tools/* "${'$'}ANDROID_HOME/cmdline-tools/latest/" || true
        fi
        export ANDROID_HOME ANDROID_SDK_ROOT="${'$'}ANDROID_HOME"
        yes | "${'$'}ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="${'$'}ANDROID_HOME" \
          "platform-tools" "platforms;android-34" "platforms;android-35" "build-tools;34.0.0" "build-tools;35.0.0" || true
        printf 'GEIVIA_ANDROID_TOOLCHAIN_OK\\n'
        java -version
        gradle --version | head -5
        """.trimIndent()
    )

    fun buildCommand(projectPath: String): List<String> {
        val safe = projectPath.replace("'", "'\\''")
        return listOf(
            "login", "debian", "--",
            "/bin/sh", "-lc",
            """
            set -eu
            cd '$safe'
            export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
            export ANDROID_HOME=/opt/android-sdk
            export ANDROID_SDK_ROOT=${'$'}ANDROID_HOME
            if [ -x ./gradlew ]; then
              chmod +x ./gradlew
              ./gradlew assembleDebug --no-daemon --stacktrace
            elif command -v gradle >/dev/null 2>&1; then
              gradle assembleDebug --no-daemon --stacktrace
            else
              echo 'Gradle não encontrado. Instale a toolchain Android primeiro.'
              exit 1
            fi
            echo 'GEIVIA_BUILD_FINISHED'
            find . -name '*.apk' -path '*/outputs/apk/*' | head -20
            """.trimIndent()
        )
    }

    fun findApk(projectRoot: File): File? {
        if (!projectRoot.isDirectory) return null
        return projectRoot.walkTopDown()
            .onEnter { it.name !in setOf(".git", "node_modules", ".gradle") }
            .filter { it.isFile && it.extension == "apk" && it.path.contains("outputs/apk") }
            .maxByOrNull { it.lastModified() }
    }
}
