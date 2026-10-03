package com.example.netmaster.domain

import com.example.netmaster.data.EngineeringScenario
import org.junit.Assert.*
import org.junit.Test

class ScenarioEngineTest {
    @Test fun exactDomainAndSymptomRetrieveScenario() {
        val scenarios = listOf(
            EngineeringScenario("s1", "DNS", "DNS outage", "Users get IP but DNS fails", "LAN", listOf("DNS service"), listOf("nslookup"), listOf("timeout"), "DNS unhealthy", "restore DNS", "rollback", listOf("dns", "resolution")),
            EngineeringScenario("s2", "VLAN", "VLAN mismatch", "VLAN 10 users cannot reach gateway", "Access", listOf("VLAN"), listOf("show vlan"), listOf("missing vlan"), "VLAN mismatch", "fix trunk", "rollback", listOf("vlan"))
        )
        val engine = ScenarioEngine(scenarios)
        val hits = engine.retrieve("users get IP but DNS resolution fails", 2)
        assertFalse(hits.isEmpty())
        assertEquals("s1", hits.first().scenario.id)
    }
}
