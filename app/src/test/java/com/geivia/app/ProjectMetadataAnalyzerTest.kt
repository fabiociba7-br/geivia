package com.geivia.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectMetadataAnalyzerTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun readsGoogleAiStudioStyleAppIdentityAndFirebaseDeployConfiguration() {
        val root = temp.newFolder("ai-studio-export")
        File(root, "package.json").writeText(
            """{"name":"gemini-starter","description":"Created with Google AI Studio","dependencies":{"@google/genai":"^1.0.0"}}"""
        )
        File(root, "index.html").writeText("<html><head><title>Meu Aplicativo</title></head></html>")
        File(root, ".firebaserc").writeText(
            """{"projects":{"default":"my-firebase-project"},"targets":{"my-firebase-project":{"hosting":{"production":["my-production-site"]}}}}"""
        )
        File(root, "firebase.json").writeText(
            """{"hosting":{"target":"production","public":"dist"},"functions":{"source":"functions"},"firestore":{"rules":"firestore.rules","indexes":"firestore.indexes.json"},"storage":{"rules":"storage.rules"},"database":{"rules":"database.rules.json"}}"""
        )
        File(root, "functions/src/index.ts").apply { parentFile!!.mkdirs(); writeText("export {}") }
        File(root, "firestore.rules").writeText("rules_version = '2';")
        File(root, "storage.rules").writeText("rules_version = '2';")
        File(root, "database.rules.json").writeText("{}")
        File(root, "dist/index.html").apply { parentFile!!.mkdirs(); writeText("<html/>") }

        val info = ProjectWorkspaceInfo.scan(root)
        val meta = info.metadata

        assertEquals("Meu Aplicativo", meta.appName)
        assertEquals("my-firebase-project", meta.firebaseProjectId)
        assertEquals(listOf("my-production-site"), meta.hostingSiteIds)
        assertTrue(meta.deployTargets.contains("hosting:production"))
        assertTrue(meta.deployTargets.contains("functions"))
        assertEquals(listOf("functions"), meta.functionsSources)
        assertEquals(listOf("firestore.rules"), meta.firestoreRules)
        assertEquals(listOf("storage.rules"), meta.storageRules)
        assertEquals(listOf("database.rules.json"), meta.databaseRules)
        assertEquals(listOf("dist"), meta.hostingPublicDirs)
        assertTrue(meta.googleAiStudioProject)
        assertTrue(meta.structure.any { it.path == "functions/src" && it.role == "Firebase Cloud Functions" })
    }

    @Test fun reportsConflictingFirebaseProjectIdsAndDoesNotInventDeployTargets() {
        val root = temp.newFolder("conflicting-project")
        File(root, ".firebaserc").writeText("""{"projects":{"default":"first-project"}}""")
        File(root, "google-services.json").writeText("""{"project_info":{"project_id":"second-project"}}""")
        File(root, "firebase.json").writeText("""{"hosting":{"public":"public"}}""")
        File(root, "public/index.html").apply { parentFile!!.mkdirs(); writeText("<html/>") }

        val meta = ProjectWorkspaceInfo.scan(root).metadata

        assertEquals("first-project", meta.firebaseProjectId)
        assertTrue(meta.warnings.any { it.contains("divergentes") })
        assertEquals(listOf("hosting"), meta.deployTargets)
        assertFalse(meta.deployTargets.contains("functions"))
    }
}
