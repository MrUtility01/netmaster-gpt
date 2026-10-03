package com.example.netmaster.domain

import com.example.netmaster.data.ReviewItemEntity
import kotlin.math.roundToInt

/** SM-2-inspired scheduler tuned for lesson mastery and short daily review loops. */
data class ReviewPlan(
    val due: List<ReviewItemEntity>,
    val overdueCount: Int,
    val dueTodayCount: Int,
    val recommendedLimit: Int,
    val nextReviewAt: Long?
)

class ReviewEngine {
    fun seed(lessonId:String, mastery:String, now:Long=System.currentTimeMillis()):ReviewItemEntity {
        val (score,days)=when(mastery) {
            "REVIEW"->2 to 1
            "KNOWN"->4 to 3
            "MASTERED"->5 to 14
            else->3 to 1
        }
        return ReviewItemEntity(lessonId,ease=2.5,intervalDays=days,repetitions=0,lapses=0,nextReviewAt=now+days*DAY,lastScore=score,updatedAt=now)
    }

    fun update(old:ReviewItemEntity?, lessonId:String, quality:Int, now:Long=System.currentTimeMillis()):ReviewItemEntity {
        val q=quality.coerceIn(0,5)
        var ease=old?.ease?:2.5
        var reps=old?.repetitions?:0
        var lapses=old?.lapses?:0
        var interval=old?.intervalDays?:1
        ease=(ease+(0.1-(5-q)*(0.08+(5-q)*0.02))).coerceIn(1.3,3.0)
        if(q<3) {
            reps=0; lapses++; interval=1
        } else {
            reps++
            interval=when(reps) {
                1->1
                2->3
                else->(interval*ease).roundToInt().coerceIn(4,180)
            }
        }
        return ReviewItemEntity(lessonId,ease,interval,reps,lapses,now+interval*DAY,q,now)
    }

    fun plan(items: List<ReviewItemEntity>, now: Long = System.currentTimeMillis(), limit: Int = 20): ReviewPlan {
        val safeLimit=limit.coerceIn(1,100)
        val due=items.filter { it.nextReviewAt <= now }.sortedWith(compareBy<ReviewItemEntity> { it.nextReviewAt }.thenBy { it.lapses })
        val dayEnd=now + DAY
        val dueToday=items.count { it.nextReviewAt in now..dayEnd }
        return ReviewPlan(
            due=due.take(safeLimit),
            overdueCount=items.count { it.nextReviewAt < now },
            dueTodayCount=dueToday,
            recommendedLimit=minOf(safeLimit, maxOf(5, (due.size * 0.6).roundToInt())),
            nextReviewAt=items.filter { it.nextReviewAt > now }.minOfOrNull { it.nextReviewAt }
        )
    }

    fun qualityFromMastery(mastery: String): Int = when (mastery.uppercase()) {
        "MASTERED" -> 5
        "KNOWN" -> 4
        "REVIEW" -> 2
        else -> 1
    }

    private companion object { const val DAY=86_400_000L }
}
