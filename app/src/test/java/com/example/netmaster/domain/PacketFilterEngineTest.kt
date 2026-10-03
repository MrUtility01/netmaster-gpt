package com.example.netmaster.domain

import com.example.netmaster.data.PacketRecord
import org.junit.Assert.*
import org.junit.Test

class PacketFilterEngineTest {
    private val engine = PacketFilterEngine()
    private val packets = listOf(
        PacketRecord(1, "0.001", "10.0.0.10", "8.8.8.8", "TCP", "HTTPS/TLS 50000 → 443 SYN,ACK", 74, 50000, 443, "SYN,ACK", ipVersion = 4, streamId = 12),
        PacketRecord(2, "0.002", "10.0.0.10", "10.0.0.20", "DNS", "DNS query", 74, 50000, 53, ipVersion = 4),
        PacketRecord(3, "0.003", "10.0.0.20", "10.0.0.10", "ICMP", "echo reply", 98, ipVersion = 4)
    )

    @Test fun matchesCompoundExpression() {
        val result = engine.filter(packets, "tcp.port == 443 and ip.src == 10.0.0.10")
        assertNull(result.error)
        assertEquals(listOf(1), result.records.map { it.number })
    }

    @Test fun supportsNotAndProtocolAliases() {
        val result = engine.filter(packets, "not dns and ip.addr == 10.0.0.10")
        assertNull(result.error)
        assertEquals(listOf(1, 3), result.records.map { it.number })
    }

    @Test fun returnsUsefulError() {
        val result = engine.filter(packets, "tcp and (")
        assertTrue(result.records.isEmpty())
        assertNotNull(result.error)
    }
}
