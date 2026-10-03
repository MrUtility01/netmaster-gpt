package com.example.netmaster.domain

import org.junit.Assert.*
import org.junit.Test

class ReviewEngineTest {
    private val engine = ReviewEngine()

    @Test fun masterySeedsDifferentIntervals() {
        val known = engine.seed("l1", "KNOWN", 1_000L)
        val mastered = engine.seed("l2", "MASTERED", 1_000L)
        assertEquals(3, known.intervalDays)
        assertEquals(14, mastered.intervalDays)
        assertTrue(mastered.nextReviewAt > known.nextReviewAt)
    }

    @Test fun failedReviewResetsIntervalAndAddsLapse() {
        val old = engine.seed("l1", "KNOWN", 1_000L)
        val next = engine.update(old, "l1", 1, 2_000L)
        assertEquals(1, next.intervalDays)
        assertEquals(1, next.lapses)
        assertEquals(0, next.repetitions)
    }
}
