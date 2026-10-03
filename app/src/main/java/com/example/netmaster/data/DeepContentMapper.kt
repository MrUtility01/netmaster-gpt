package com.example.netmaster.data

object DeepContentMapper {
    fun toCurriculum(base:Curriculum, pack:DeepContentPack):Curriculum {
        val levels=base.levels + pack.tracks.map { track ->
            Level(
                id=track.id,
                title="${track.id} — ${track.title}",
                summary="${track.domain}: ${track.scope}",
                lessons=track.lessons.map { it.toLesson() }
            )
        }
        return base.copy(
            levels=levels,
            metadata=base.metadata?.copy(
                version="3.1.0-deep",
                levels=levels.size,
                lessonCount=levels.sumOf { it.lessons.size },
                description="50 Core Levels + advanced curriculum + 32 Deep Engineering Tracks"
            )
        )
    }

    fun scenariosAsLessons(pack:ScenarioPack): List<Lesson> = pack.scenarios.map { s ->
        Lesson(
            id="scenario:${s.id}",
            title="Scenario — ${s.title}",
            goal="حل Incident با روش Evidence-driven در حوزه ${s.domain}.",
            simple="Symptom: ${s.symptom}\nScope: ${s.scope}",
            technical="Hypotheses:\n${s.hypotheses.joinToString("\n")}",
            diagram="Symptom → Scope → Hypothesis → Test → Evidence → Finding → Resolution → Rollback",
            example=s.title,
            commands=s.tests.joinToString("\n"),
            traffic=s.evidence.joinToString("\n"),
            lab="${s.expectedFinding}\n\nResolution: ${s.resolution}",
            troubleshooting="${s.expectedFinding}\nRollback: ${s.rollback}",
            questions=listOf("کدام Evidence فرضیه را رد می‌کند؟","اولین Test کم‌خطر چیست؟"),
            tags=s.tags + listOf(s.domain,"scenario"),
            deepTechnical=s.scope,
            packetWalkthrough=s.evidence.joinToString("\n"),
            configurationPlaybook="Validate → Change → Verify → Observe → Rollback",
            platformCommands=s.tests.joinToString("\n"),
            realScenario=s.title,
            failureMatrix=listOf(FailureCase(s.symptom,s.hypotheses.firstOrNull().orEmpty(),s.evidence.joinToString(", "),s.tests.firstOrNull().orEmpty())),
            evidenceChecklist=s.evidence,
            labSteps=s.tests,
            interviewQuestions=listOf("Root Cause چیست و چه Evidenceای آن را ثابت می‌کند؟"),
            masteryPath=listOf("Symptom را تعریف می‌کنم","Hypothesis می‌سازم","Test اجرا می‌کنم","Evidence جمع می‌کنم","Resolution و Rollback را ثبت می‌کنم"),
            topicSpecific=listOf(s.domain,s.scope),
            keyPoints=listOf(s.expectedFinding),
            commonMistakes=listOf("تغییر چند متغیر هم‌زمان","حل symptom بدون اثبات root cause"),
            studyChecklist=listOf("Scope","Evidence","Test","Finding","Resolution","Rollback")
        )
    }

    private fun DeepLesson.toLesson()=Lesson(
        id=id,title=title,goal=goal,simple=simple,technical=technical,diagram=diagram,example=example,commands=commands,traffic=traffic,
        lab=lab,troubleshooting=troubleshooting,questions=questions,tags=tags,deepTechnical=deepTechnical,packetWalkthrough=packetWalkthrough,
        configurationPlaybook=configurationPlaybook,platformCommands=platformCommands,realScenario=realScenario,failureMatrix=failureMatrix,
        evidenceChecklist=evidenceChecklist,labSteps=labSteps,interviewQuestions=interviewQuestions,masteryPath=masteryPath,topicSpecific=topicSpecific,
        keyPoints=keyPoints,commonMistakes=commonMistakes,studyChecklist=studyChecklist
    )
}
