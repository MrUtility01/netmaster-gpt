package com.example.netmaster.domain

import org.junit.Assert.*
import org.junit.Test

class DigitalTwinTest {
    private val engine = DigitalTwinEngine()

    @Test fun repairThenVerifyRequiresEvidence() {
        val fault = engine.faults().first { it.id == "dns" }
        var state = engine.inject(engine.defaultState(), fault)
        assertFalse(engine.verify(state).passed)
        state = engine.repair(state)
        assertFalse(engine.verify(state).passed)
        state = engine.recordEvidence(state, "nslookup failed before fix")
        state = engine.recordEvidence(state, "nslookup succeeds after fix")
        val v = engine.verify(state)
        assertTrue(v.passed)
        assertEquals(100, v.score)
        assertNull(v.remainingFault)
    }

    @Test fun unrelatedEvidenceDoesNotPassVerification() {
        val fault = engine.faults().first { it.id == "dns" }
        var state = engine.repair(engine.inject(engine.defaultState(), fault))
        state = engine.recordEvidence(state, "show vlan brief")
        assertFalse(engine.verify(state).passed)
        state = engine.recordEvidence(state, "nslookup example.com succeeds")
        assertTrue(engine.verify(state).passed)
    }

    @Test fun completeClearsOnlyVerifiedFault() {
        val fault = engine.faults().first { it.id == "route" }
        var state = engine.inject(engine.defaultState(), fault)
        state = engine.repair(state)
        state = engine.recordEvidence(state, "show ip route before/after")
        assertNull(engine.complete(state).activeFault)
    }
}
