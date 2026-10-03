package com.example.netmaster.data

import kotlinx.serialization.Serializable

@Serializable
data class DeepContentMetadata(
    val version:String="",
    val language:String="",
    val trackCount:Int=0,
    val lessonCount:Int=0,
    val method:List<String> = emptyList(),
    val description:String=""
)

@Serializable
data class DeepContentPack(
    val metadata:DeepContentMetadata = DeepContentMetadata(),
    val tracks:List<DeepTrack> = emptyList()
)

@Serializable
data class DeepTrack(
    val id:Int,
    val title:String,
    val domain:String,
    val scope:String,
    val lessons:List<DeepLesson> = emptyList()
)

@Serializable
data class DeepLesson(
    val id:String,
    val title:String,
    val goal:String,
    val simple:String,
    val technical:String,
    val diagram:String,
    val example:String,
    val commands:String,
    val traffic:String,
    val lab:String,
    val troubleshooting:String,
    val questions:List<String> = emptyList(),
    val tags:List<String> = emptyList(),
    val deepTechnical:String="",
    val packetWalkthrough:String="",
    val configurationPlaybook:String="",
    val platformCommands:String="",
    val realScenario:String="",
    val failureMatrix:List<FailureCase> = emptyList(),
    val evidenceChecklist:List<String> = emptyList(),
    val labSteps:List<String> = emptyList(),
    val interviewQuestions:List<String> = emptyList(),
    val masteryPath:List<String> = emptyList(),
    val topicSpecific:List<String> = emptyList(),
    val keyPoints:List<String> = emptyList(),
    val commonMistakes:List<String> = emptyList(),
    val studyChecklist:List<String> = emptyList()
)

@Serializable
data class ScenarioPack(
    val metadata:ScenarioMetadata = ScenarioMetadata(),
    val scenarios:List<EngineeringScenario> = emptyList()
)

@Serializable
data class ScenarioMetadata(val version:String="", val scenarioCount:Int=0)

@Serializable
data class EngineeringScenario(
    val id:String,
    val domain:String,
    val title:String,
    val symptom:String,
    val scope:String="",
    val hypotheses:List<String> = emptyList(),
    val evidence:List<String> = emptyList(),
    val tests:List<String> = emptyList(),
    val expectedFinding:String="",
    val resolution:String="",
    val rollback:String="",
    val tags:List<String> = emptyList()
)

data class EngineeringScenarioHit(val scenario:EngineeringScenario,val score:Float)
