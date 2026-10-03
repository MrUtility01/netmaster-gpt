package com.example.netmaster.data

import kotlinx.serialization.Serializable

@Serializable
data class MasterCurriculum(
    val schemaVersion: String = "",
    val product: String = "",
    val version: String = "",
    val language: String = "",
    val purpose: String = "",
    val contentModel: MasterContentModel = MasterContentModel(),
    val phases: List<MasterPhase> = emptyList(),
    val integration: Map<String, List<String>> = emptyMap(),
    val mastery: List<String> = emptyList(),
    val recommendedJourney: List<String> = emptyList()
)

@Serializable
data class MasterContentModel(
    val core: String = "",
    val deep: String = "",
    val scenarios: String = "",
    val fieldEngineering: String = "",
    val lessonPipeline: List<String> = emptyList()
)

@Serializable
data class MasterPhase(
    val id: Int,
    val name: String,
    val levels: List<Int> = emptyList(),
    val topics: List<String> = emptyList(),
    val platforms: List<String> = emptyList(),
    val labModes: List<String> = emptyList()
)

@Serializable
data class LabCatalog(val version: String = "", val labs: List<LabDefinition> = emptyList())

@Serializable
data class LabDefinition(
    val id: String,
    val title: String,
    val phase: String = "",
    val mode: String = "",
    val objective: String = "",
    val topology: String = "",
    val inputs: List<String> = emptyList(),
    val steps: List<String> = emptyList(),
    val acceptance: List<String> = emptyList(),
    val safety: String = ""
)

@Serializable
data class AiPlaybookCatalog(
    val version: String = "",
    val architecture: List<String> = emptyList(),
    val retrieval: AiRetrieval = AiRetrieval(),
    val modes: Map<String, String> = emptyMap(),
    val networkAiRules: List<String> = emptyList()
)

@Serializable
data class AiRetrieval(
    val sources: List<String> = emptyList(),
    val strategy: String = "",
    val grounding: String = ""
)

/** Unified searchable representation. It deliberately contains no secrets or runtime state. */
data class UnifiedCatalogEntry(
    val kind: String,
    val id: String,
    val title: String,
    val body: String,
    val tags: List<String> = emptyList(),
    val action: String = ""
)
