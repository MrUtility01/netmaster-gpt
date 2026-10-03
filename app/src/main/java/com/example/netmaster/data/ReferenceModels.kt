package com.example.netmaster.data

import kotlinx.serialization.Serializable

@Serializable
data class ReferenceCatalog(
    val version: String = "",
    val catalog: String = "",
    val description: String = "",
    val entries: List<ReferenceEntry> = emptyList()
)

@Serializable
data class ReferenceEntry(
    val id: String,
    val title: String,
    val summary: String = "",
    val tags: List<String> = emptyList(),
    val layers: List<String> = emptyList(),
    val ports: List<Int> = emptyList(),
    val states: List<String> = emptyList(),
    val commands: List<String> = emptyList(),
    val steps: List<String> = emptyList(),
    val observables: List<String> = emptyList(),
    val failureStops: List<String> = emptyList(),
    val symptom: String = "",
    val firstEvidence: String = "",
    val discriminatingTest: String = "",
    val resolution: String = "",
    val rollback: String = "",
    val sourceUrls: List<String> = emptyList()
)

data class ReferenceBundle(
    val specialistBooks: ReferenceCatalog,
    val protocols: ReferenceCatalog,
    val commands: ReferenceCatalog,
    val faults: ReferenceCatalog,
    val packetJourneys: ReferenceCatalog
)
