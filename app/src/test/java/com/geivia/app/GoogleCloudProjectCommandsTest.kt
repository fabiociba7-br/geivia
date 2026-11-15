package com.geivia.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleCloudProjectCommandsTest {
    @Test
    fun createOrReuseOnlyCreatesAfterExactNotFoundAndOnlyOnce() {
        val command = GoogleCloudProjectCommands.createOrReuse("geivia-demo-123", "Projeto de teste")
        assertTrue(command.contains("gcloud projects describe \"\$PROJECT_ID\""))
        assertTrue(command.contains("grep -Eq '^ERROR: \\(gcloud\\.projects\\.describe\\) NOT_FOUND:'"))
        assertTrue(command.contains("if ! gcloud projects create"))
        assertTrue(command.contains("Consulta inconclusiva ou sem permissão. Nenhuma criação foi enviada."))
        assertTrue(command.contains("NÃO repita. Reconcilie o mesmo Project ID."))
        assertTrue(command.contains(".lifecycleState == \"ACTIVE\""))
        assertTrue(command.contains("O projeto existe, mas ainda não está ACTIVE."))
        assertEquals(1, Regex("gcloud projects create").findAll(command).count())
        assertFalse(command.contains("grep -Eqi"))
        assertFalse(command.contains("NOT_FOUND|not found"))
    }

    @Test
    fun existingProjectIsReusedAndAmbiguousResultDoesNotTriggerSecondCreate() {
        val command = GoogleCloudProjectCommands.createOrReuse("geivia-demo-123", "Projeto de teste")
        assertTrue(command.contains("Projeto já existe e está acessível. Nenhuma criação será feita."))
        assertTrue(command.contains("Não foi possível confirmar o resultado. NÃO crie outro projeto."))
        assertTrue(command.contains("exit 22"))
        assertEquals(1, Regex("gcloud projects create").findAll(command).count())
    }

    @Test
    fun reconciliationOnlyDescribesTheOriginalProjectId() {
        val command = GoogleCloudProjectCommands.reconcile("geivia-demo-123")
        assertTrue(command.contains("gcloud projects describe 'geivia-demo-123'"))
        assertTrue(command.contains("NOT_FOUND_CONFIRMED"))
        assertTrue(command.contains("grep -Eq '^ERROR: \\(gcloud\\.projects\\.describe\\) NOT_FOUND:'"))
        assertFalse(command.contains("gcloud projects create"))
    }

    @Test
    fun displayNameIsBase64EncodedAndCannotInjectShellCommands() {
        val displayName = "Projeto '$(touch /tmp/geivia-pwned)'; \"teste\""
        val command = GoogleCloudProjectCommands.createOrReuse("geivia-demo-123", displayName)
        val encoded = java.util.Base64.getEncoder().encodeToString(displayName.toByteArray(Charsets.UTF_8))

        assertTrue(command.contains("PROJECT_NAME=\"$(printf '%s' '$encoded' | base64 -d)\""))
        assertFalse(command.contains(displayName))
        assertFalse(command.contains("touch /tmp/geivia-pwned"))
        assertEquals(displayName, String(java.util.Base64.getDecoder().decode(encoded), Charsets.UTF_8))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidProjectIdCannotBeUsedForCreation() {
        GoogleCloudProjectCommands.createOrReuse("Not A Valid ID", "Projeto")
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankDisplayNameCannotBeUsedForCreation() {
        GoogleCloudProjectCommands.createOrReuse("geivia-demo-123", " ")
    }
}
