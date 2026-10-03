package com.example.netmaster.search

import com.example.netmaster.data.*

/**
 * Product-wide catalog search. It complements the persisted GlobalSearch database by
 * indexing static product knowledge: tracks, labs, scenarios and AI playbooks.
 */
class UnifiedCatalogSearch {
    private var entries: List<UnifiedCatalogEntry> = emptyList()

    fun rebuild(
        curriculum: Curriculum,
        deep: DeepContentPack,
        scenarios: ScenarioPack,
        master: MasterCurriculum,
        labs: LabCatalog,
        ai: AiPlaybookCatalog,
        references: ReferenceBundle = ReferenceBundle(ReferenceCatalog(), ReferenceCatalog(), ReferenceCatalog(), ReferenceCatalog(), ReferenceCatalog())
    ) {
        val result = mutableListOf<UnifiedCatalogEntry>()
        curriculum.levels.forEach { level ->
            result += UnifiedCatalogEntry("LEVEL", "level:${level.id}", level.title,
                level.summary, tags = listOf("curriculum", "level", level.id.toString()))
        }
        master.phases.forEach { phase ->
            result += UnifiedCatalogEntry("TRACK", "track:${phase.id}", phase.name,
                (listOf(phase.name) + phase.topics + phase.platforms + phase.labModes).joinToString("\n"),
                phase.platforms + phase.labModes + "phase:${phase.id}")
        }
        deep.tracks.forEach { track ->
            result += UnifiedCatalogEntry("DEEP_TRACK", "deep-track:${track.id}", track.title,
                listOf(track.domain, track.scope, track.lessons.joinToString(" ") { it.title + " " + it.goal }).joinToString("\n"),
                listOf(track.domain, "deep"))
        }
        scenarios.scenarios.forEach { s ->
            result += UnifiedCatalogEntry("SCENARIO", s.id, s.title,
                listOf(s.domain, s.symptom, s.scope, s.hypotheses.joinToString(" "), s.evidence.joinToString(" "), s.tests.joinToString(" "), s.expectedFinding, s.resolution, s.rollback).joinToString("\n"),
                s.tags + s.domain)
        }
        labs.labs.forEach { l ->
            result += UnifiedCatalogEntry("LAB", l.id, l.title,
                listOf(l.phase, l.mode, l.objective, l.topology, l.inputs.joinToString(" "), l.steps.joinToString(" "), l.acceptance.joinToString(" "), l.safety).joinToString("\n"),
                listOf(l.phase, l.mode, "lab"))
        }
        ai.modes.forEach { (mode, description) ->
            result += UnifiedCatalogEntry("AI_PLAYBOOK", "ai:$mode", mode, description, listOf("ai", "playbook", mode))
        }
        referencesList(references).forEach { r ->
            val body = listOf(
                r.catalog, r.entry.title, r.entry.summary, r.entry.tags.joinToString(" "),
                r.entry.layers.joinToString(" "), r.entry.states.joinToString(" "),
                r.entry.commands.joinToString(" "), r.entry.steps.joinToString(" "),
                r.entry.observables.joinToString(" "), r.entry.failureStops.joinToString(" "),
                r.entry.symptom, r.entry.firstEvidence, r.entry.discriminatingTest,
                r.entry.resolution, r.entry.rollback
            ).filter { it.isNotBlank() }.joinToString("\n")
            result += UnifiedCatalogEntry("REFERENCE", "ref:${r.catalog}:${r.entry.id}", r.entry.title, body, r.entry.tags + "reference:${r.catalog}", r.entry.sourceUrls.firstOrNull().orEmpty())
        }
        entries = result.distinctBy { it.kind + ":" + it.id }
    }

    private data class RefWithCatalog(val catalog: String, val entry: ReferenceEntry)

    private fun referencesList(bundle: ReferenceBundle): List<RefWithCatalog> = buildList {
        bundle.specialistBooks.entries.forEach { add(RefWithCatalog(bundle.specialistBooks.catalog, it)) }
        bundle.protocols.entries.forEach { add(RefWithCatalog(bundle.protocols.catalog, it)) }
        bundle.commands.entries.forEach { add(RefWithCatalog(bundle.commands.catalog, it)) }
        bundle.faults.entries.forEach { add(RefWithCatalog(bundle.faults.catalog, it)) }
        bundle.packetJourneys.entries.forEach { add(RefWithCatalog(bundle.packetJourneys.catalog, it)) }
    }

    fun search(query: String, limit: Int = 30): List<GlobalSearchHit> {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return emptyList()
        return entries.mapNotNull { e ->
            val hay = tokenize(listOf(e.title, e.body, e.tags.joinToString(" ")).joinToString(" "))
            val title = tokenize(e.title)
            val hits = tokens.count { it in hay }
            if (hits == 0) null else {
                val titleHits = tokens.count { it in title }
                val score = hits * 12 + titleHits * 10 + if (tokens.any { e.tags.any { tag -> tokenize(tag).contains(it) } }) 6 else 0
                GlobalSearchHit(e.kind, e.id, e.title, e.body.replace('\n', ' ').take(280), score)
            }
        }.sortedByDescending { it.score }.take(limit)
    }

    fun size() = entries.size
    fun byKind(kind: String) = entries.count { it.kind == kind }

    private fun tokenize(value: String) = value.lowercase()
        .replace('ي', 'ی').replace('ك', 'ک')
        .split(Regex("[^\\p{L}\\p{N}._:/-]+"))
        .filter { it.length > 1 }
}
