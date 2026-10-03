package com.example.netmaster.export

import android.content.Context
import com.example.netmaster.data.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class NetMasterBackup(
    val version: Int = 4,
    val exportedAt: Long,
    val curriculumVersion: String? = null,
    val progress: List<ProgressEntityDto>,
    val notes: List<NoteEntityDto>,
    val bookmarks: List<String>,
    val incidents: List<IncidentDto> = emptyList(),
    val incidentEvidence: List<IncidentEvidenceDto> = emptyList(),
    val incidentEvents: List<IncidentEventDto> = emptyList(),
    val configs: List<ConfigDto> = emptyList(),
    val captures: List<CaptureDto> = emptyList(),
    val reviews: List<ReviewDto> = emptyList(),
    val ragVectors: List<RagVectorDto> = emptyList(),
    val knowledgeNodes: List<KnowledgeNodeDto> = emptyList(),
    val knowledgeEdges: List<KnowledgeEdgeDto> = emptyList(),
    val simulatorSnapshots: List<SimulatorSnapshotDto> = emptyList(),
    val coachRuns: List<CoachRunDto> = emptyList()
)

@Serializable
data class ProgressEntityDto(
    val lessonId: String,
    val mastery: String,
    val completed: Boolean,
    val updatedAt: Long,
    val nextReviewAt: Long
)

@Serializable
data class NoteEntityDto(
    val id: Long,
    val lessonId: String?,
    val title: String,
    val body: String,
    val tags: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class IncidentDto(
    val id: Long,
    val title: String,
    val symptom: String,
    val status: String,
    val severity: String,
    val rootCause: String,
    val resolution: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class IncidentEvidenceDto(
    val id: Long,
    val incidentId: Long,
    val type: String,
    val value: String,
    val createdAt: Long
)

@Serializable
data class IncidentEventDto(
    val id: Long,
    val incidentId: Long,
    val eventType: String,
    val message: String,
    val createdAt: Long
)

@Serializable
data class ConfigDto(
    val id: Long,
    val deviceId: String,
    val vendor: String,
    val label: String,
    val content: String,
    val fingerprint: String,
    val createdAt: Long
)

@Serializable
data class CaptureDto(
    val id: Long,
    val name: String,
    val format: String,
    val rawText: String,
    val packetCount: Int,
    val protocolSummary: String,
    val createdAt: Long
)

@Serializable
data class ReviewDto(
    val lessonId: String,
    val ease: Double,
    val intervalDays: Int,
    val repetitions: Int,
    val lapses: Int,
    val nextReviewAt: Long,
    val lastScore: Int,
    val updatedAt: Long
)

@Serializable
data class RagVectorDto(
    val id: String,
    val title: String,
    val text: String,
    val source: String,
    val tags: String,
    val vector: String,
    val updatedAt: Long
)

@Serializable
data class KnowledgeNodeDto(
    val id: String,
    val title: String,
    val kind: String,
    val content: String,
    val tags: String,
    val updatedAt: Long
)

@Serializable
data class KnowledgeEdgeDto(
    val fromId: String,
    val toId: String,
    val relation: String,
    val weight: Double
)

@Serializable
data class SimulatorSnapshotDto(
    val id: Long,
    val label: String,
    val stateJson: String,
    val createdAt: Long
)

@Serializable
data class CoachRunDto(
    val id: Long,
    val symptom: String,
    val scenarioId: String?,
    val status: String,
    val transcript: String,
    val createdAt: Long,
    val updatedAt: Long
)

object ExportManager {
    fun createBackup(
        context: Context,
        curriculum: Curriculum?,
        progress: List<ProgressEntity>,
        notes: List<NoteEntity>,
        bookmarks: List<BookmarkEntity>,
        incidents: List<IncidentEntity> = emptyList(),
        evidence: List<IncidentEvidenceEntity> = emptyList(),
        configs: List<ConfigSnapshotEntity> = emptyList(),
        captures: List<PacketCaptureEntity> = emptyList(),
        reviews: List<ReviewItemEntity> = emptyList(),
        vectors: List<RagVectorEntity> = emptyList(),
        knowledgeNodes: List<KnowledgeNodeEntity> = emptyList(),
        knowledgeEdges: List<KnowledgeEdgeEntity> = emptyList(),
        simulatorSnapshots: List<SimulatorSnapshotEntity> = emptyList(),
        coachRuns: List<CoachRunEntity> = emptyList(),
        events: List<IncidentEventEntity> = emptyList()
    ): File {
        val payload = NetMasterBackup(
            exportedAt = System.currentTimeMillis(),
            curriculumVersion = curriculum?.metadata?.version,
            progress = progress.map {
                ProgressEntityDto(it.lessonId, it.mastery, it.completed, it.updatedAt, it.nextReviewAt)
            },
            notes = notes.map {
                NoteEntityDto(it.id, it.lessonId, it.title, it.body, it.tags, it.createdAt, it.updatedAt)
            },
            bookmarks = bookmarks.map { it.lessonId },
            incidents = incidents.map {
                IncidentDto(
                    it.id, it.title, it.symptom, it.status, it.severity,
                    it.rootCause, it.resolution, it.createdAt, it.updatedAt
                )
            },
            incidentEvidence = evidence.map {
                IncidentEvidenceDto(it.id, it.incidentId, it.type, it.value, it.createdAt)
            },
            incidentEvents = events.map {
                IncidentEventDto(it.id, it.incidentId, it.eventType, it.message, it.createdAt)
            },
            configs = configs.map {
                ConfigDto(
                    it.id, it.deviceId, it.vendor, it.label,
                    it.content, it.fingerprint, it.createdAt
                )
            },
            captures = captures.map {
                CaptureDto(
                    it.id, it.name, it.format, it.rawText,
                    it.packetCount, it.protocolSummary, it.createdAt
                )
            },
            reviews = reviews.map {
                ReviewDto(
                    it.lessonId, it.ease, it.intervalDays, it.repetitions,
                    it.lapses, it.nextReviewAt, it.lastScore, it.updatedAt
                )
            },
            ragVectors = vectors.map {
                RagVectorDto(it.id, it.title, it.text, it.source, it.tags, it.vector, it.updatedAt)
            },
            knowledgeNodes = knowledgeNodes.map {
                KnowledgeNodeDto(it.id, it.title, it.kind, it.content, it.tags, it.updatedAt)
            },
            knowledgeEdges = knowledgeEdges.map {
                KnowledgeEdgeDto(it.fromId, it.toId, it.relation, it.weight)
            },
            simulatorSnapshots = simulatorSnapshots.map {
                SimulatorSnapshotDto(it.id, it.label, it.stateJson, it.createdAt)
            },
            coachRuns = coachRuns.map {
                CoachRunDto(
                    it.id, it.symptom, it.scenarioId, it.status,
                    it.transcript, it.createdAt, it.updatedAt
                )
            }
        )
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        return File(dir, "netmaster-backup-1.0-${System.currentTimeMillis()}.json").also {
            it.writeText(Json { prettyPrint = true }.encodeToString(payload))
        }
    }
}
