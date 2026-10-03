package com.example.netmaster.domain

import org.junit.Assert.*
import org.junit.Test

class ConfigDiffEngineTest {
    private val engine = ConfigDiffEngine()

    @Test fun detectsAddedAndRemovedLines() {
        val diff = engine.diff("interface vlan10\ndescription users\n", "interface vlan10\ndescription clients\nip address 10.0.0.1/24\n")
        assertTrue(diff.any { it.type == "-" && it.line == "description users" })
        assertTrue(diff.any { it.type == "+" && it.line == "description clients" })
        assertTrue(diff.any { it.type == "+" && it.line.contains("10.0.0.1") })
    }

    @Test fun fingerprintIgnoresLineEndingAndBlankLines() {
        assertEquals(engine.fingerprint("a\r\n\r\nb\r\n"), engine.fingerprint("a\nb\n"))
    }
}
