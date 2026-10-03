package com.example.netmaster.domain

import org.junit.Assert.*
import org.junit.Test

class ConfigDiffEngineDepthTest {
    private val engine = ConfigDiffEngine()

    @Test fun classifiesDisruptiveSecurityAndRoutingChanges() {
        val old = """
            interface Gi1/0/1
             ip address 10.0.0.1 255.255.255.0
            ip route 10.20.0.0 255.255.0.0 10.0.0.2
        """.trimIndent()
        val newer = """
            interface Gi1/0/1
             shutdown
            ip route 10.20.0.0 255.255.0.0 10.0.0.3
            ip access-list extended USERS
             deny ip any any
        """.trimIndent()
        val summary = engine.summarize(old, newer)
        assertTrue(summary.riskScore >= 6)
        assertTrue(summary.sections.isNotEmpty())
        assertTrue(summary.semanticChanges.any { it.contains("Drop", true) || it.contains("Access", true) })
    }
}
