plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing is supplied only by protected GitHub Actions secrets.
// Never commit the keystore or passwords to the repository.
val releaseKeystoreFile = System.getenv("GEIVIA_KEYSTORE_FILE")
val releaseKeystorePassword = System.getenv("GEIVIA_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("GEIVIA_KEY_ALIAS")
val releaseKeyPassword = System.getenv("GEIVIA_KEY_PASSWORD")
val releaseSigningConfigured = listOf(
    releaseKeystoreFile,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.geivia.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.geivia.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.0.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (releaseSigningConfigured) {
                storeFile = file(requireNotNull(releaseKeystoreFile))
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            // CI refuses to publish an unsigned or differently signed APK.
            signingConfig = if (releaseSigningConfigured) {
                signingConfigs.getByName("release")
            } else {
                null
            }
            isMinifyEnabled = false
        }
    }

    // Native executables must live in Android nativeLibraryDir, not noexec filesDir.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":proot-engine"))
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    // Local Android test stubs do not implement org.json; use the current
    // Maven Central implementation only on the JVM test classpath.
    testImplementation("org.json:json:20260814")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
