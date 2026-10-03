package com.example.netmaster.data

import androidx.room.*
import kotlinx.serialization.Serializable

@Entity(tableName="knowledge_nodes", indices=[Index("kind"), Index("title")])
data class KnowledgeNodeEntity(@PrimaryKey val id:String,val title:String,val kind:String,val content:String,val tags:String="",val updatedAt:Long=System.currentTimeMillis())

@Entity(tableName="knowledge_edges", primaryKeys=["fromId","toId","relation"])
data class KnowledgeEdgeEntity(val fromId:String,val toId:String,val relation:String,val weight:Double=1.0)

@Entity(tableName="incidents", indices=[Index("status"),Index("severity"),Index("updatedAt")])
data class IncidentEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val title:String,val symptom:String,val status:String="OPEN",val severity:String="MEDIUM",val rootCause:String="",val resolution:String="",val createdAt:Long=System.currentTimeMillis(),val updatedAt:Long=System.currentTimeMillis())

@Entity(tableName="incident_evidence", indices=[Index("incidentId"),Index("type"),Index("createdAt")])
data class IncidentEvidenceEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val incidentId:Long,val type:String,val value:String,val createdAt:Long=System.currentTimeMillis())

@Entity(tableName="incident_events", indices=[Index("incidentId"),Index("createdAt")])
data class IncidentEventEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val incidentId:Long,val eventType:String,val message:String,val createdAt:Long=System.currentTimeMillis())

@Entity(tableName="config_snapshots", indices=[Index("deviceId"),Index("vendor"),Index("createdAt")])
data class ConfigSnapshotEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val deviceId:String,val vendor:String,val label:String,val content:String,val fingerprint:String,val createdAt:Long=System.currentTimeMillis())

@Entity(tableName="packet_captures", indices=[Index("createdAt"),Index("format")])
data class PacketCaptureEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val name:String,val format:String,val rawText:String,val packetCount:Int,val protocolSummary:String,val createdAt:Long=System.currentTimeMillis())

@Entity(tableName="review_items", indices=[Index("nextReviewAt"),Index("lessonId")])
data class ReviewItemEntity(@PrimaryKey val lessonId:String,val ease:Double=2.5,val intervalDays:Int=1,val repetitions:Int=0,val lapses:Int=0,val nextReviewAt:Long=System.currentTimeMillis(),val lastScore:Int=0,val updatedAt:Long=System.currentTimeMillis())

@Entity(tableName="rag_vectors", indices=[Index("source")])
data class RagVectorEntity(@PrimaryKey val id:String,val title:String,val text:String,val source:String,val tags:String="",val vector:String,val updatedAt:Long=System.currentTimeMillis())

@Entity(tableName="simulator_snapshots", indices=[Index("createdAt")])
data class SimulatorSnapshotEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val label:String,val stateJson:String,val createdAt:Long=System.currentTimeMillis())

@Entity(tableName="coach_runs", indices=[Index("createdAt"),Index("status")])
data class CoachRunEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val symptom:String,val scenarioId:String?,val status:String,val transcript:String,val createdAt:Long=System.currentTimeMillis(),val updatedAt:Long=System.currentTimeMillis())

@Serializable
data class RagChunk(val id: String,val title: String,val text: String,val tags: List<String> = emptyList(),val source: String = "curriculum")

@Serializable
data class PacketRecord(
    val number:Int,
    val timestamp:String="",
    val source:String="",
    val destination:String="",
    val protocol:String="",
    val info:String="",
    val length:Int=0,
    val srcPort:Int?=null,
    val dstPort:Int?=null,
    val tcpFlags:String="",
    val vlanId:Int?=null,
    val ipVersion:Int?=null,
    val streamId:Int?=null
)

@Serializable
data class ConfigDiffLine(val type:String,val line:String,val lineNumber:Int=0)

data class KnowledgeGraphView(val nodes:List<KnowledgeNodeEntity>,val edges:List<KnowledgeEdgeEntity>)
data class ReviewDue(val item:ReviewItemEntity,val lessonTitle:String)
data class IncidentWorkspace(val incident:IncidentEntity,val evidence:List<IncidentEvidenceEntity>,val events:List<IncidentEventEntity>,val linkedCaptures:List<PacketCaptureEntity>,val linkedConfigs:List<ConfigSnapshotEntity>)
