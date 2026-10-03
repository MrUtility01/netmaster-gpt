package com.example.netmaster.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName="progress",primaryKeys=["lessonId"])
data class ProgressEntity(val lessonId:String,val mastery:String="UNKNOWN",val completed:Boolean=false,val updatedAt:Long=System.currentTimeMillis(),val nextReviewAt:Long=System.currentTimeMillis())
@Entity(tableName="notes",indices=[Index("lessonId"),Index("updatedAt")])
data class NoteEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val lessonId:String?,val title:String,val body:String,val tags:String="",val createdAt:Long=System.currentTimeMillis(),val updatedAt:Long=System.currentTimeMillis())
@Entity(tableName="bookmarks")
data class BookmarkEntity(@PrimaryKey val lessonId:String,val createdAt:Long=System.currentTimeMillis())
@Entity(tableName="study_sessions")
data class StudySessionEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val startedAt:Long,val minutes:Int)

@Dao
interface AppDao {
 @Query("SELECT * FROM progress") suspend fun progress():List<ProgressEntity>
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun upsertProgress(p:ProgressEntity)
 @Query("SELECT * FROM notes ORDER BY updatedAt DESC") suspend fun notes():List<NoteEntity>
 @Insert suspend fun insertNote(n:NoteEntity):Long
 @Update suspend fun updateNote(n:NoteEntity)
 @Delete suspend fun deleteNote(n:NoteEntity)
 @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC") suspend fun bookmarks():List<BookmarkEntity>
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun bookmark(b:BookmarkEntity)
 @Query("DELETE FROM bookmarks WHERE lessonId=:id") suspend fun unbookmark(id:String)
 @Insert suspend fun session(s:StudySessionEntity)

 @Query("SELECT * FROM knowledge_nodes ORDER BY updatedAt DESC") suspend fun knowledgeNodes():List<KnowledgeNodeEntity>
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun knowledgeNode(n:KnowledgeNodeEntity)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun knowledgeEdge(e:KnowledgeEdgeEntity)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun knowledgeNodesBatch(n:List<KnowledgeNodeEntity>)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun knowledgeEdgesBatch(e:List<KnowledgeEdgeEntity>)
 @Query("SELECT * FROM knowledge_edges") suspend fun knowledgeEdges():List<KnowledgeEdgeEntity>

 @Query("SELECT * FROM incidents ORDER BY updatedAt DESC") suspend fun incidents():List<IncidentEntity>
 @Insert suspend fun incident(i:IncidentEntity):Long
 @Update suspend fun updateIncident(i:IncidentEntity)
 @Insert suspend fun evidence(e:IncidentEvidenceEntity):Long
 @Query("SELECT * FROM incident_evidence WHERE incidentId=:id ORDER BY createdAt") suspend fun evidence(id:Long):List<IncidentEvidenceEntity>
 @Query("SELECT * FROM incident_evidence ORDER BY createdAt") suspend fun allIncidentEvidence():List<IncidentEvidenceEntity>
 @Insert suspend fun incidentEvent(e:IncidentEventEntity):Long
 @Query("SELECT * FROM incident_events WHERE incidentId=:id ORDER BY createdAt") suspend fun incidentEvents(id:Long):List<IncidentEventEntity>
 @Query("SELECT * FROM incident_events ORDER BY createdAt") suspend fun allIncidentEvents():List<IncidentEventEntity>
 @Query("SELECT * FROM incidents WHERE id=:id LIMIT 1") suspend fun incidentById(id:Long):IncidentEntity?

 @Insert suspend fun config(c:ConfigSnapshotEntity):Long
 @Query("SELECT * FROM config_snapshots WHERE deviceId=:device ORDER BY createdAt DESC") suspend fun configs(device:String):List<ConfigSnapshotEntity>
 @Query("SELECT * FROM config_snapshots ORDER BY createdAt DESC") suspend fun allConfigs():List<ConfigSnapshotEntity>
 @Query("SELECT * FROM config_snapshots WHERE id=:id LIMIT 1") suspend fun configById(id:Long):ConfigSnapshotEntity?

 @Insert suspend fun capture(c:PacketCaptureEntity):Long
 @Query("SELECT * FROM packet_captures ORDER BY createdAt DESC") suspend fun captures():List<PacketCaptureEntity>
 @Query("SELECT * FROM packet_captures WHERE id=:id LIMIT 1") suspend fun captureById(id:Long):PacketCaptureEntity?

 @Query("SELECT * FROM review_items WHERE nextReviewAt<=:now ORDER BY nextReviewAt LIMIT :limit") suspend fun dueReviews(now:Long,limit:Int):List<ReviewItemEntity>
 @Query("SELECT * FROM review_items ORDER BY nextReviewAt LIMIT :limit") suspend fun upcomingReviews(limit:Int):List<ReviewItemEntity>
 @Query("SELECT * FROM review_items WHERE lessonId=:id LIMIT 1") suspend fun reviewByLesson(id:String):ReviewItemEntity?
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun review(r:ReviewItemEntity)
 @Query("SELECT * FROM review_items ORDER BY nextReviewAt") suspend fun allReviews():List<ReviewItemEntity>

 @Query("SELECT * FROM rag_vectors ORDER BY updatedAt DESC") suspend fun ragVectors():List<RagVectorEntity>
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun ragVector(v:RagVectorEntity)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun ragVectorsBatch(v:List<RagVectorEntity>)
 @Query("DELETE FROM rag_vectors") suspend fun clearRagVectors()

 @Insert suspend fun simulatorSnapshot(s:SimulatorSnapshotEntity):Long
 @Query("SELECT * FROM simulator_snapshots ORDER BY createdAt DESC") suspend fun simulatorSnapshots():List<SimulatorSnapshotEntity>
 @Insert suspend fun coachRun(r:CoachRunEntity):Long
 @Query("SELECT * FROM coach_runs ORDER BY updatedAt DESC") suspend fun coachRuns():List<CoachRunEntity>
}

@Database(
 entities=[ProgressEntity::class,NoteEntity::class,BookmarkEntity::class,StudySessionEntity::class,KnowledgeNodeEntity::class,KnowledgeEdgeEntity::class,IncidentEntity::class,IncidentEvidenceEntity::class,IncidentEventEntity::class,ConfigSnapshotEntity::class,PacketCaptureEntity::class,ReviewItemEntity::class,RagVectorEntity::class,SimulatorSnapshotEntity::class,CoachRunEntity::class],
 version=5,exportSchema=false)
abstract class AppDatabase:RoomDatabase(){
 abstract fun dao():AppDao
 companion object {
  @Volatile private var INSTANCE:AppDatabase?=null
  private val MIGRATION_3_4=object:Migration(3,4){override fun migrate(db:SupportSQLiteDatabase){
   db.execSQL("CREATE TABLE IF NOT EXISTS rag_vectors (id TEXT NOT NULL, title TEXT NOT NULL, text TEXT NOT NULL, source TEXT NOT NULL, tags TEXT NOT NULL, vector TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))")
   db.execSQL("CREATE INDEX IF NOT EXISTS index_rag_vectors_source ON rag_vectors(source)")
  }}
  private val MIGRATION_4_5=object:Migration(4,5){override fun migrate(db:SupportSQLiteDatabase){
   db.execSQL("CREATE TABLE IF NOT EXISTS incident_events (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, incidentId INTEGER NOT NULL, eventType TEXT NOT NULL, message TEXT NOT NULL, createdAt INTEGER NOT NULL)")
   db.execSQL("CREATE INDEX IF NOT EXISTS index_incident_events_incidentId ON incident_events(incidentId)")
   db.execSQL("CREATE INDEX IF NOT EXISTS index_incident_events_createdAt ON incident_events(createdAt)")
   db.execSQL("CREATE TABLE IF NOT EXISTS simulator_snapshots (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, label TEXT NOT NULL, stateJson TEXT NOT NULL, createdAt INTEGER NOT NULL)")
   db.execSQL("CREATE INDEX IF NOT EXISTS index_simulator_snapshots_createdAt ON simulator_snapshots(createdAt)")
   db.execSQL("CREATE TABLE IF NOT EXISTS coach_runs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, symptom TEXT NOT NULL, scenarioId TEXT, status TEXT NOT NULL, transcript TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
   db.execSQL("CREATE INDEX IF NOT EXISTS index_coach_runs_createdAt ON coach_runs(createdAt)")
   db.execSQL("CREATE INDEX IF NOT EXISTS index_coach_runs_status ON coach_runs(status)")
  }}
  fun get(context:Context)=INSTANCE?: synchronized(this){INSTANCE?:Room.databaseBuilder(context.applicationContext,AppDatabase::class.java,"netmaster.db").addMigrations(MIGRATION_3_4,MIGRATION_4_5).fallbackToDestructiveMigration().build().also{INSTANCE=it}}
 }
}
