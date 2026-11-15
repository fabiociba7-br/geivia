package com.geivia.app

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class ShellCommandSyntaxTest {
    private fun assertValidBash(script: String) {
        val bashAvailable = runCatching {
            ProcessBuilder("bash", "--version").redirectErrorStream(true).start().waitFor() == 0
        }.getOrDefault(false)
        assumeTrue("Bash não está disponível neste ambiente de testes.", bashAvailable)

        val process = ProcessBuilder("bash", "-n").redirectErrorStream(true).start()
        process.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(script) }
        val output = process.inputStream.bufferedReader(Charsets.UTF_8).readText()
        val exitCode = process.waitFor()
        assertEquals("Script Bash inválido:\n$output", 0, exitCode)
    }

    @Test
    fun projectCreationCommandHasValidBashSyntax() {
        assertValidBash(GoogleCloudProjectCommands.createOrReuse("geivia-demo-123", "Projeto de teste"))
    }

    @Test
    fun projectReconciliationCommandHasValidBashSyntax() {
        assertValidBash(GoogleCloudProjectCommands.reconcile("geivia-demo-123"))
    }

    @Test
    fun firestoreRulesCommandHasValidBashSyntax() {
        assertValidBash(FirebaseDeploymentCommands.firestoreRulesBootstrap("geivia-demo-123"))
    }

    @Test
    fun hostingDeployCommandHasValidBashSyntax() {
        assertValidBash(FirebaseDeploymentCommands.hostingStarterDeploy("geivia-demo-123"))
    }
}
