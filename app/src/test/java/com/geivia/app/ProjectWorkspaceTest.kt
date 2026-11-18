package com.geivia.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectWorkspaceTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun scansAndroidProjectAndGradleModules() {
        val root = temp.newFolder("sample-app")
        File(root, "settings.gradle.kts").writeText("include(\":app\")")
        File(root, "app/src/main/AndroidManifest.xml").apply {
            parentFile!!.mkdirs()
            writeText("<manifest />")
        }
        File(root, "app/build.gradle.kts").writeText("plugins {}")
        File(root, "app/src/main/java/example/Main.kt").apply {
            parentFile!!.mkdirs()
            writeText("package example")
        }

        val info = ProjectWorkspaceInfo.scan(root)

        assertEquals(ProjectWorkspaceInfo.Kind.ANDROID, info.kind)
        assertTrue(info.hasManifest)
        assertTrue(info.moduleNames.contains("app"))
        assertTrue(info.relativeFiles.contains("app/src/main/java/example/Main.kt"))
    }

    @Test fun ignoresGeneratedDirectoriesButKeepsProjectSources() {
        val root = temp.newFolder("web-project")
        File(root, "index.html").writeText("<html />")
        File(root, "src/main.js").apply {
            parentFile!!.mkdirs()
            writeText("console.log('ok')")
        }
        File(root, "node_modules/pkg/index.js").apply {
            parentFile!!.mkdirs()
            writeText("ignored")
        }
        File(root, "build/output.js").apply {
            parentFile!!.mkdirs()
            writeText("ignored")
        }

        val info = ProjectWorkspaceInfo.scan(root)

        assertEquals(ProjectWorkspaceInfo.Kind.WEB, info.kind)
        assertTrue(info.relativeFiles.contains("src/main.js"))
        assertFalse(info.relativeFiles.any { it.startsWith("node_modules/") })
        assertFalse(info.relativeFiles.any { it.startsWith("build/") })
    }

    @Test fun resolvesSingleNestedProjectRoot() {
        val selected = temp.newFolder("selected")
        val nested = File(selected, "source/project")
        assertTrue(nested.mkdirs())
        File(nested, "settings.gradle").writeText("rootProject.name='sample'")

        assertEquals(nested.canonicalFile, ProjectWorkspaceInfo.resolveRoot(selected))
    }
}
