package com.geivia.app

import java.io.File

/**
 * On-device Android build using the existing GEIVIA Debian/PRoot environment.
 * Uses Debian's default JDK package so the installer follows the active stable release.
 */
internal object GeiviaAndroidBuild {
    fun installToolchain(): List<String> = listOf(
        "login", "debian", "--",
        "/bin/sh", "-lc",
        """
        set -eu
        export DEBIAN_FRONTEND=noninteractive

        apt-get update
        apt-get install -y --no-install-recommends default-jdk-headless wget unzip ca-certificates

        # Derive JAVA_HOME from the JDK actually installed by Debian (stable may use JDK 21).
        JAVAC_PATH="$(readlink -f "$(command -v javac)")"
        export JAVA_HOME="$(dirname "$(dirname "$JAVAC_PATH")")"
        export PATH="$JAVA_HOME/bin:$PATH"

        # Install a pinned Gradle distribution only when it is not already available.
        if ! command -v gradle >/dev/null 2>&1; then
          cd /tmp
          wget -q https://services.gradle.org/distributions/gradle-8.10.2-bin.zip -O gradle-8.10.2-bin.zip
          mkdir -p /opt
          unzip -q -o gradle-8.10.2-bin.zip -d /opt
          ln -sf /opt/gradle-8.10.2/bin/gradle /usr/local/bin/gradle
          rm -f /tmp/gradle-8.10.2-bin.zip
        fi

        ANDROID_HOME=/opt/android-sdk
        export ANDROID_HOME ANDROID_SDK_ROOT="$ANDROID_HOME"
        export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
        mkdir -p "$ANDROID_HOME/cmdline-tools"
        if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
          cd /tmp
          wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdtools.zip
          rm -rf /tmp/cmdtools
          mkdir -p /tmp/cmdtools "$ANDROID_HOME/cmdline-tools/latest"
          unzip -q cmdtools.zip -d /tmp/cmdtools
          cp -a /tmp/cmdtools/cmdline-tools/. "$ANDROID_HOME/cmdline-tools/latest/"
          rm -rf /tmp/cmdtools /tmp/cmdtools.zip
        fi

        yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" --licenses
        "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" \
          "platform-tools" "platforms;android-34" "platforms;android-35" "build-tools;34.0.0" "build-tools;35.0.0"

        command -v java
        command -v javac
        command -v gradle
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
            JAVAC_PATH="$(readlink -f "$(command -v javac)")"
            export JAVA_HOME="$(dirname "$(dirname "$JAVAC_PATH")")"
            export PATH="$JAVA_HOME/bin:$PATH"
            export ANDROID_HOME=/opt/android-sdk
            export ANDROID_SDK_ROOT="$ANDROID_HOME"
            export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
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
