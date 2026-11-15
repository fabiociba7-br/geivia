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
        assertTrue(command.contains("RULES_HTTP"))
        assertTrue(command.contains("releases/cloud.firestore"))
        assertTrue(command.contains("Nenhuma publicação foi feita"))
        assertTrue(command.contains("allow read, write: if false"))
        assertTrue(command.contains("firebase deploy --only firestore:rules"))
        assertFalse(command.contains("firebase deploy --only hosting"))
    }

    @Test
    fun hostingCommandChecksSiteAndDeploysOnlyHosting() {
        val command = FirebaseDeploymentCommands.hostingStarterDeploy("geivia-sample-123")
        assertTrue(command.contains("SITE_HTTP"))
        assertTrue(command.contains("[ \"\$SITE_HTTP\" = \"404\" ]"))
        assertTrue(command.contains("SITE_READY"))
        assertTrue(command.contains(".type == \"DEFAULT_SITE\""))
        assertTrue(command.contains("URL_READY"))
        assertTrue(command.contains("firebase deploy --only hosting"))
        assertTrue(command.contains("https://\$PROJECT_ID.web.app"))
        assertFalse(command.contains("firebase deploy --only functions"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidProjectIdIsRejectedBeforeGeneratingCommands() {
        FirebaseDeploymentCommands.hostingStarterDeploy("Not A Valid ID")
    }

    @Test
    fun firestoreRulesCommandStopsOnAmbiguousHttpStatusAndProtectsExistingFolder() {
        val command = FirebaseDeploymentCommands.firestoreRulesBootstrap("geivia-sample-123")
        assertTrue(command.contains("[ \"\$RULES_HTTP\" = \"200\" ]"))
        assertTrue(command.contains("[ \"\$RULES_HTTP\" != \"404\" ]"))
        assertTrue(command.contains("exit 25"))
        assertTrue(command.contains("if [ -e \"\$WORKDIR\" ]; then"))
        assertTrue(command.contains("! -f \"\$WORKDIR/.geivia-generated\""))
        assertTrue(command.contains("A pasta de destino não corresponde exatamente à estrutura gerada pelo GEIVIA. Nada foi alterado."))
    }

    @Test
    fun hostingCommandCreatesOnlyAfterExactNotFoundAndStopsOnOtherStatuses() {
        val command = FirebaseDeploymentCommands.hostingStarterDeploy("geivia-sample-123")
        assertTrue(command.contains("[ \"\$SITE_HTTP\" = \"404\" ]"))
        assertTrue(command.contains("[ \"\$SITE_HTTP\" != \"200\" ]"))
        assertTrue(command.contains("exit 6"))
        assertTrue(command.contains("projects/\$PROJECT_ID/sites?siteId=\$SITE_ID"))
        assertTrue(command.contains("O site ainda não propagou. Não crie outro site"))
        assertTrue(command.contains("if [ -e \"\$WORKDIR\" ]; then"))
        assertTrue(command.contains("! -f \"\$WORKDIR/.geivia-generated\""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidProjectIdIsRejectedForFirestoreRulesCommand() {
        FirebaseDeploymentCommands.firestoreRulesBootstrap("invalid;touch-pwned")
    }
    @Test
    fun firestoreRulesCommandValidatesLocalDirectoryBeforeAnyCloudMutation() {
        val command = FirebaseDeploymentCommands.firestoreRulesBootstrap("geivia-sample-123")
        val localGuard = command.indexOf("A pasta de destino é um link simbólico")
        val firebaseRegistration = command.indexOf("firebase projects:addfirebase")
        val rulesPublish = command.indexOf("firebase deploy --only firestore:rules")
        assertTrue(localGuard >= 0)
        assertTrue(firebaseRegistration > localGuard)
        assertTrue(rulesPublish > firebaseRegistration)
        assertTrue(command.contains("[ -L \"\$WORKDIR/.geivia-generated\" ]"))
        assertTrue(command.contains("[ -L \"\$WORKDIR/firebase.json\" ]"))
        assertTrue(command.contains("[ -L \"\$WORKDIR/firestore.rules\" ]"))
        assertTrue(command.contains("GEIVIA_FIRESTORE_RULES_STARTER_V1"))
        assertTrue(command.contains(".firestore.rules == \"firestore.rules\""))
        assertTrue(command.contains("A configuração Firestore local não aponta para firestore.rules. Nada foi publicado."))
        assertTrue(command.indexOf("A configuração Firestore local não aponta para firestore.rules. Nada foi publicado.") < command.indexOf("gcloud projects describe"))
        assertTrue(command.contains("cmp -s \"\$WORKDIR/firestore.rules\" \"\$EXPECTED_RULES_FILE\""))
        assertTrue(command.contains("O arquivo firestore.rules foi alterado em relação ao modelo deny-all do GEIVIA. Nada foi publicado."))
        assertTrue(command.contains("O arquivo firestore.rules não corresponde ao modelo deny-all esperado. Nada foi publicado."))
        assertTrue(command.indexOf("O arquivo firestore.rules foi alterado em relação ao modelo deny-all do GEIVIA. Nada foi publicado.") < command.indexOf("gcloud projects describe"))
        assertTrue(command.contains("mkdir \"\$WORKDIR\""))
    }

    @Test
    fun hostingCommandValidatesLocalDirectoryBeforeCreatingRemoteSite() {
        val command = FirebaseDeploymentCommands.hostingStarterDeploy("geivia-sample-123")
        val localGuard = command.indexOf("A pasta de destino é um link simbólico")
        val siteCreate = command.indexOf("-X POST")
        val hostingDeploy = command.indexOf("firebase deploy --only hosting")
        assertTrue(localGuard >= 0)
        assertTrue(siteCreate > localGuard)
        assertTrue(hostingDeploy > siteCreate)
        assertTrue(command.contains("[ -L \"\$WORKDIR/.geivia-generated\" ]"))
        assertTrue(command.contains("[ -L \"\$WORKDIR/firebase.json\" ]"))
        assertTrue(command.contains("[ -L \"\$WORKDIR/public\" ]"))
        assertTrue(command.contains("[ -L \"\$WORKDIR/public/index.html\" ]"))
        assertTrue(command.contains("GEIVIA_HOSTING_STARTER_V1"))
        assertTrue(command.contains(".hosting.site == \$id and .hosting.public == \"public\""))
        assertTrue(command.contains("A configuração Hosting local não corresponde ao site padrão deste projeto. Nada foi criado ou publicado."))
        assertTrue(command.indexOf("A configuração Hosting local não corresponde ao site padrão deste projeto. Nada foi criado ou publicado.") < command.indexOf("gcloud projects describe"))
        assertTrue(command.contains("mkdir \"\$WORKDIR\""))
    }

}
