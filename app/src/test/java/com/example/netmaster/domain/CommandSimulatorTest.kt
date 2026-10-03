package com.example.netmaster.domain

import org.junit.Assert.*
import org.junit.Test

class CommandSimulatorTest {
    private val simulator = CommandSimulator()

    @Test fun showRouteReadsCurrentState() {
        val result = simulator.execute(simulator.defaultState(), "show ip route")
        assertTrue(result.success)
        assertTrue(result.output.contains("192.168.10.0/24"))
    }

    @Test fun vlanMutationChangesStateWithoutRealExecution() {
        val start = simulator.defaultState()
        val result = simulator.execute(start, "no vlan 10")
        assertTrue(result.success)
        assertTrue(result.changed)
        assertFalse(10 in result.state.vlans)
    }

    @Test fun unsupportedCommandIsExplicit() {
        val result = simulator.execute(simulator.defaultState(), "rm -rf /")
        assertFalse(result.success)
        assertFalse(result.changed)
    }
}
