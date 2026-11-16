package com.geivia.app

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalWorkspaceImporterTest {
    @Test fun sanitizesTraversalAndSeparators() {
        assertEquals("segredo.txt", TerminalWorkspaceImporter.sanitizeDisplayName("../../segredo.txt"))
        assertEquals("foto.png", TerminalWorkspaceImporter.sanitizeDisplayName("pasta\\foto.png"))
    }

    @Test fun replacesUnsupportedCharactersAndBoundsName() {
        assertEquals("meu_arquivo_.txt", TerminalWorkspaceImporter.sanitizeDisplayName("meu:arquivo?.txt"))
        assertEquals(100, TerminalWorkspaceImporter.sanitizeDisplayName("a".repeat(150)).length)
    }

    @Test fun fallsBackForBlankAndDotNames() {
        assertEquals("arquivo-importado", TerminalWorkspaceImporter.sanitizeDisplayName("..."))
        assertEquals("arquivo-importado", TerminalWorkspaceImporter.sanitizeDisplayName("   "))
    }
}
