package com.example.netmaster.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldEngineeringEngineTest {
    private val engine = FieldEngineeringEngine()

    @Test fun dhcpEvidenceFindsFirstDivergence() {
        val r = engine.investigate("کلاینت IP نمی گیرد", "DHCP Discover observed; Offer absent; relay giaddr=192.168.10.1")
        assertTrue(r.firstDivergence.contains("DHCP"))
        assertTrue(r.hypotheses.first().title.contains("DHCP"))
        assertTrue(r.evidence.any { it.type == "PACKET" })
    }

    @Test fun dependencyReportCapturesDnsImpact() {
        val d = engine.dependencyReport("DNS resolver timeout breaks application and AD")
        assertTrue(d.any { it.node == "Application" })
        assertTrue(d.any { it.node == "AD/Kerberos" })
    }

    @Test fun faultCatalogHasRollbackForEveryCase() {
        val fs = engine.faultCatalog()
        assertEquals(8, fs.size)
        assertTrue(fs.all { it.rollback.isNotBlank() && it.firstDivergence.isNotBlank() })
    }
}
