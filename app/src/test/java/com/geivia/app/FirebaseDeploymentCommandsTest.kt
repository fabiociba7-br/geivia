package com.geivia.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseDeploymentCommandsTest {
    @Test
    fun firestoreRulesCommandIsDenyAllAndRefusesToOverwriteExistingRelease() {
        val command = FirebaseDeploymentCommands.firestoreRulesBootstrap("geivia-sample-123")
        assertTrue(command.contains("firebaserules.googleapis.com"))
        assertTrue(command.contains("releases/cloud.firestore"))
        assertTrue(command.contains("Já existe uma versão ativa"))
        assertTrue(command.contains("allow read, write: if false"))
        assertTrue(command.contains("firebase deploy --only firestore:rules"))
        assertFalse(command.contains("firebase deploy --only hosting"))
    }

    @Test
    fun hostingCommandChecksSiteAndDeploysOnlyHosting() {
        val command = FirebaseDeploymentCommands.hostingStarterDeploy("geivia-sample-123")
        assertTrue(command.contains("SITE_HTTP"))
        assertTrue(command.contains("[ \"$SITE_HTTP\" = \"404\" ]"))
        assertTrue(command.contains("firebase deploy --only hosting"))
        assertTrue(command.contains("https://$PROJECT_ID.web.app"))
        assertFalse(command.contains("firebase deploy --only functions"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidProjectIdIsRejectedBeforeGeneratingCommands() {
        FirebaseDeploymentCommands.hostingStarterDeploy("Not A Valid ID")
    }
}
