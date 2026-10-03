package com.example.netmaster.domain

import com.example.netmaster.data.ReviewItemEntity
import org.junit.Assert.*
import org.junit.Test

class ReviewEngineDepthTest {
    @Test fun planPrioritizesOverdueAndBoundsLimit() {
        val engine = ReviewEngine()
        val now = 1_000_000L
        val items = listOf(
            ReviewItemEntity("late", nextReviewAt = now - 10),
            ReviewItemEntity("today", nextReviewAt = now + 100),
            ReviewItemEntity("future", nextReviewAt = now + 10_000_000)
        )
        val plan = engine.plan(items, now, 1)
        assertEquals(1, plan.due.size)
        assertEquals("late", plan.due.first().lessonId)
        assertEquals(1, plan.overdueCount)
        assertNotNull(plan.nextReviewAt)
    }
}
