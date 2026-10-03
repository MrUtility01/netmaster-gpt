package com.example.netmaster.domain

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcapParserTest {
    @Test fun parsesClassicPcapTcpFrame() {
        val frame = ethernetIpv4Tcp()
        val pcap = classicPcap(frame)
        val result = PcapParser().parse(pcap)
        assertEquals(1, result.size)
        assertEquals("TCP", result.first().protocol)
        assertEquals(12345, result.first().srcPort)
        assertEquals(443, result.first().dstPort)
        assertTrue("SYN" in result.first().tcpFlags)
    }

    @Test fun parsesPcapNgEnhancedPacketBlock() {
        val frame = ethernetIpv4Tcp()
        val pcapng = pcapNg(frame)
        val result = PcapParser().parse(pcapng)
        assertEquals(1, result.size)
        assertEquals("TCP", result.first().protocol)
        assertEquals(443, result.first().dstPort)
    }

    private fun ethernetIpv4Tcp(): ByteArray {
        val ip = ByteArray(20).also {
            it[0] = 0x45
            putU16(it, 2, 40)
            it[8] = 64
            it[9] = 6
            it[12] = 10; it[13] = 0; it[14] = 0; it[15] = 10
            it[16] = 8; it[17] = 8; it[18] = 8; it[19] = 8
        }
        val tcp = ByteArray(20).also {
            putU16(it, 0, 12345); putU16(it, 2, 443)
            it[12] = 0x50
            it[13] = 0x02
        }
        return ByteArray(14 + ip.size + tcp.size).also {
            it[0] = 0x00; it[1] = 0x11; it[2] = 0x22; it[3] = 0x33; it[4] = 0x44; it[5] = 0x55
            it[6] = 0x66; it[7] = 0x77; it[8] = 0x88.toByte(); it[9] = 0x99.toByte(); it[10] = 0xaa.toByte(); it[11] = 0xbb.toByte()
            putU16(it, 12, 0x0800)
            ip.copyInto(it, 14); tcp.copyInto(it, 34)
        }
    }

    private fun classicPcap(frame: ByteArray): ByteArray {
        val out = ByteArray(24 + 16 + frame.size)
        ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0, 0xa1b2c3d4.toInt()); putShort(4, 2); putShort(6, 4)
            putInt(8, 0); putInt(12, 0); putInt(16, 65535); putInt(20, 1)
            putInt(24, 1); putInt(28, 2); putInt(32, frame.size); putInt(36, frame.size)
            position(40); put(frame)
        }
        return out
    }

    private fun pcapNg(frame: ByteArray): ByteArray {
        val shb = block(0x0A0D0D0A, ByteArray(16).also {
            ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).apply { putLong(0, 0x1a2b3c4dL); putShort(8, 1); putShort(10, 0) }
        })
        val idbBody = ByteArray(8).also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).apply { putShort(0, 1); putShort(2, 0); putInt(4, 65535) } }
        val idb = block(1, idbBody)
        val epbBody = ByteArray(20 + frame.size).also {
            ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(0, 0); putInt(4, 0); putInt(8, 1); putInt(12, 0); putInt(16, frame.size); position(20); put(frame)
            }
        }
        return shb + idb + block(6, epbBody)
    }

    private fun block(type: Int, body: ByteArray): ByteArray {
        val total = 12 + body.size + ((4 - (body.size % 4)) % 4)
        return ByteArray(total).also {
            ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(0, type); putInt(4, total); position(8); put(body); putInt(total - 4, total)
            }
        }
    }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 8).toByte()
        bytes[offset + 1] = value.toByte()
    }
}
