package com.example.netmaster.data

import kotlinx.serialization.Serializable

@Serializable
data class Curriculum(val levels: List<Level>, val metadata: CourseMetadata? = null)
@Serializable
data class CourseMetadata(val version:String="", val language:String="", val lessonCount:Int=0, val levels:Int=0, val method:List<String> = emptyList(), val labStack:List<String> = emptyList(), val enterpriseScenario:EnterpriseScenario?=null, val reviewStates:List<String> = emptyList(), val description:String="")
@Serializable
data class EnterpriseScenario(val vlans:List<Int> = emptyList(), val names:List<String> = emptyList(), val tasks:List<String> = emptyList())
@Serializable
data class Level(val id:Int,val title:String,val summary:String,val lessons:List<Lesson>)
@Serializable
data class FailureCase(val symptom:String="", val hypothesis:String="", val evidence:String="", val next:String="")
@Serializable
data class QuizQuestion(val q:String,val a:String)
@Serializable
data class Lesson(
    val id:String,val title:String,val goal:String,val simple:String,val technical:String,val diagram:String,val example:String,val commands:String,val traffic:String,val lab:String,val troubleshooting:String,
    val questions:List<String>,val tags:List<String>,
    val deepTechnical:String = "", val packetWalkthrough:String = "", val configurationPlaybook:String = "", val platformCommands:String = "", val realScenario:String = "", val failureMatrix:List<FailureCase> = emptyList(), val evidenceChecklist:List<String> = emptyList(), val labSteps:List<String> = emptyList(), val interviewQuestions:List<String> = emptyList(), val masteryPath:List<String> = emptyList(), val topicSpecific:List<String> = emptyList(),
    val keyPoints:List<String> = emptyList(), val commonMistakes:List<String> = emptyList(), val studyChecklist:List<String> = emptyList(), val quiz:List<QuizQuestion> = emptyList(),
    val verification:String = "", val failure_analysis:String = "", val packet_state_walkthrough:String = "",
    val expert_scenario:String = "", val production_design:String = "", val expert_reference:String = ""
)

enum class Mastery { UNKNOWN, REVIEW, KNOWN, MASTERED }
