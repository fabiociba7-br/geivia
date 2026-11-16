package com.geivia.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class LocalTerminalEngineTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private fun engine() = LocalTerminalEngine(temporaryFolder.root)

    @Test fun helpAndBasicCommandsWork() {
        val terminal = engine()
        assertTrue(terminal.execute("help").contains("comandos disponíveis"))
        assertTrue(terminal.execute("pwd").contains("terminal-workspace"))
        assertTrue(terminal.execute("whoami").contains("geivia"))
        assertEquals("olá mundo", terminal.execute("echo olá mundo"))
    }

    @Test fun filesStayInsidePrivateWorkspace() {
        val terminal = engine()
        assertTrue(terminal.execute("mkdir projetos").startsWith("Diretório criado"))
        assertTrue(terminal.execute("touch projetos/nota.txt").startsWith("Arquivo disponível"))
        assertTrue(terminal.execute("ls projetos").contains("nota.txt"))
        assertTrue(terminal.execute("cat ../outside.txt").contains("Acesso fora"))
    }

    @Test fun arbitraryCommandsAreNotExecuted() {
        val terminal = engine()
        assertTrue(terminal.execute("id").contains("Comando não disponível"))
        assertTrue(terminal.execute("sh -c id").contains("Comando não disponível"))
    }

    @Test fun oversizedInputIsRejected() {
        assertTrue(engine().execute("echo " + "x".repeat(2100)).contains("excede o limite"))
    }
}
