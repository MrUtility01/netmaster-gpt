package com.example.netmaster.domain

import org.junit.Assert.*
import org.junit.Test

class PacketAnalysisEngineTest {
    private val engine = PacketAnalysisEngine()

    @Test fun detectsHandshakeFailureAndDora() {
        val records = engine.parse(
            """
            1 10.0.0.10:50000 -> 10.0.0.20:443 TCP SYN
            2 10.0.0.10:50000 -> 10.0.0.20:443 TCP Retransmission SYN
            3 DHCP DISCOVER
            4 DHCP OFFER
            5 DHCP REQUEST
            6 DHCP ACK
            """.trimIndent()
        )
        val report = engine.analyze(records)
        assertEquals(1, report.handshakeFailures)
        assertEquals(1, report.dhcpDoraComplete)
        assertTrue(report.timeline.any { it.kind == "DHCP" })
    }

    @Test fun completesBidirectionalTcpHandshake() {
        val records = engine.parse(
            """
            1 10.0.0.1:50000 -> 10.0.0.2:443 TCP SYN
            2 10.0.0.2:443 -> 10.0.0.1:50000 TCP SYN ACK
            3 10.0.0.1:50000 -> 10.0.0.2:443 TCP ACK
            """.trimIndent()
        )
        val report = engine.analyze(records)
        assertEquals(1, report.handshakeComplete)
        assertEquals(0, report.handshakeFailures)
    }

    @Test fun reportsTopConversations() {
        val records = engine.parse(
            """
            1 10.0.0.1:40000 -> 10.0.0.2:443 TCP SYN
            2 10.0.0.1:40000 -> 10.0.0.2:443 TCP ACK
            3 10.0.0.1:40000 -> 10.0.0.2:443 TCP ACK
            """.trimIndent()
        )
        val report = engine.analyze(records)
        assertEquals(1, report.topConversations.size)
        assertEquals(3, report.topConversations.first().packets)
    }
}
