package com.example.netmaster.data

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class ContentRepository(private val context:Context) {
    private val json=Json{ignoreUnknownKeys=true}

    fun loadCurriculum():Curriculum=load("curriculum.json")
    /** Legacy duplicate pack is intentionally not loaded as a second curriculum. */
    fun loadDeepPack():DeepContentPack=DeepContentPack()
    fun loadScenarios():ScenarioPack=load("deep/scenarios.json")
    fun loadMasterCurriculum():MasterCurriculum=load("master/netmaster_master_curriculum.json")
    fun loadLabs():LabCatalog=load("master/lab_catalog.json")
    fun loadAiPlaybooks():AiPlaybookCatalog=load("master/ai_playbooks.json")
    fun loadReferences():ReferenceBundle = ReferenceBundle(
        specialistBooks = load("references/specialist_books.json"),
        protocols = load("references/protocol_catalog.json"),
        commands = load("references/commands.json"),
        faults = load("references/fault_matrix.json"),
        packetJourneys = load("references/packet_journeys.json")
    )

    private inline fun <reified T> load(path:String):T {
        return context.assets.open(path).bufferedReader(Charsets.UTF_8).use { json.decodeFromString(it.readText()) }
    }
}
