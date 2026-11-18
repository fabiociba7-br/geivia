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

    @Test fun resolvesProjectInsideWrapperEvenWhenItHasModules() {
        val selected = temp.newFolder("wrapper")
        val project = File(selected, "geivia")
        File(project, "app/src/main/AndroidManifest.xml").apply { parentFile!!.mkdirs(); writeText("<manifest />") }
        File(project, "build.gradle.kts").writeText("plugins {}")
        File(project, "app/build.gradle.kts").writeText("plugins {}")
        File(project, "worker/package.json").apply { parentFile!!.mkdirs(); writeText("{}") }

        assertEquals(project.canonicalFile, ProjectWorkspaceInfo.resolveRoot(selected))
    }

    @Test fun resolveRootIgnoresMacZipMetadata() {
        val selected = temp.newFolder("zip")
        File(selected, "__MACOSX/project/build.gradle").apply { parentFile!!.mkdirs(); writeText("") }
        val project = File(selected, "project")
        File(project, "settings.gradle").apply { parentFile!!.mkdirs(); writeText("") }

        assertEquals(project.canonicalFile, ProjectWorkspaceInfo.resolveRoot(selected))
    }

    @Test fun keepsSelectedFolderWhenSeveralProjectsAreAmbiguous() {
        val selected = temp.newFolder("many")
        File(selected, "a/build.gradle").apply { parentFile!!.mkdirs(); writeText("") }
        File(selected, "b/build.gradle").apply { parentFile!!.mkdirs(); writeText("") }

        assertEquals(selected.canonicalFile, ProjectWorkspaceInfo.resolveRoot(selected))
    }

    @Test fun scanResolvesWrapperRoot() {
        val selected = temp.newFolder("outer")
        val project = File(selected, "inner")
        File(project, "settings.gradle.kts").apply { parentFile!!.mkdirs(); writeText("") }
        File(project, "app/build.gradle.kts").apply { parentFile!!.mkdirs(); writeText("") }

        val info = ProjectWorkspaceInfo.scan(selected)

        assertEquals(project.canonicalFile, info.root)
        assertEquals(ProjectWorkspaceInfo.Kind.ANDROID, info.kind)
        assertTrue(info.relativeFiles.contains("settings.gradle.kts"))
    }
}
