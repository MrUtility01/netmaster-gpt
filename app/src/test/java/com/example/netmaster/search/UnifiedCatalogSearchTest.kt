package com.example.netmaster.search

import com.example.netmaster.data.*
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedCatalogSearchTest {
    @Test
    fun searchesStaticEngineeringCatalog() {
        val engine = UnifiedCatalogSearch()
        engine.rebuild(
            Curriculum(listOf(Level(1, "مبانی شبکه", "IP و Ethernet", emptyList()))),
            DeepContentPack(tracks = listOf(DeepTrack(1, "Routing Engineering", "Routing", "OSPF", emptyList()))),
            ScenarioPack(scenarios = listOf(EngineeringScenario("S1", "Routing", "OSPF Neighbor Down", "Adjacency is down", tags = listOf("OSPF")))),
            MasterCurriculum(phases = listOf(MasterPhase(1, "Routing Control/Data Plane", topics = listOf("OSPF")))),
            LabCatalog(labs = listOf(LabDefinition("LAB-1", "OSPF Fault Injection", mode = "Fault Injection"))),
            AiPlaybookCatalog(modes = mapOf("TROUBLESHOOTER" to "Evidence driven troubleshooting"))
        )
        val results = engine.search("OSPF")
        assertTrue(results.any { it.kind == "SCENARIO" })
        assertTrue(results.any { it.kind == "LAB" })
        assertTrue(results.any { it.kind == "TRACK" })
    }
}
