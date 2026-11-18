package com.geivia.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.io.File

/**
 * Bridge to Code on the Go (GPL-3.0, https://github.com/appdevforall/CodeOnTheGo).
 * Opens the active GEIVIA project in Code on the Go so the user can compile it on-device.
 */
internal object CodeOnTheGoBridge {
    private const val COGO_PACKAGE = "com.itsaky.androidide"
    private const val COGO_DEEP_LINK_BASE = "https://appdevforall.org/device/open/project/"
    private const val DOWNLOAD_URL = "https://www.appdevforall.org/code-on-the-go/"

    fun isInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(COGO_PACKAGE, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun openProject(context: Context, projectDir: File?): Boolean {
        if (projectDir == null || !projectDir.isDirectory) {
            Toast.makeText(context, "Nenhum projeto ativo para abrir no Code on the Go.", Toast.LENGTH_LONG).show()
            return false
        }
        if (!isInstalled(context)) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(DOWNLOAD_URL))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
            Toast.makeText(context, "Code on the Go não está instalado. Abrindo página de download.", Toast.LENGTH_LONG).show()
            return false
        }

        val projectName = projectDir.name
        val deepLink = Uri.parse(COGO_DEEP_LINK_BASE + Uri.encode(projectName))
        val deepIntent = Intent(Intent.ACTION_VIEW, deepLink)
            .setPackage(COGO_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (deepIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(deepIntent)
            Toast.makeText(context, "Abrindo projeto no Code on the Go…", Toast.LENGTH_SHORT).show()
            return true
        }

        val launch = context.packageManager.getLaunchIntentForPackage(COGO_PACKAGE)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
            Toast.makeText(context, "Code on the Go aberto. Importe a pasta do projeto manualmente se necessário.", Toast.LENGTH_LONG).show()
            return true
        }

        Toast.makeText(context, "Não foi possível abrir o Code on the Go.", Toast.LENGTH_LONG).show()
        return false
    }
}
