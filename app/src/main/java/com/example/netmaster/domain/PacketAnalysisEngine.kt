package com.example.netmaster.domain

import com.example.netmaster.data.PacketRecord
import kotlin.math.max

data class ConversationKey(val source:String,val destination:String,val srcPort:Int?,val dstPort:Int?,val protocol:String)
data class ConversationSummary(val key:ConversationKey,val packets:Int,val resets:Int,val retransmissions:Int,val dnsErrors:Int)
data class PacketTimelineEvent(val packet:Int,val kind:String,val detail:String)
private data class BidirectionalConversationKey(val left:String,val right:String,val leftPort:Int?,val rightPort:Int?,val protocol:String)
data class PacketAnalysisReport(
    val total:Int,
    val protocolCounts:Map<String,Int>,
    val conversations:Map<ConversationKey,Int>,
    val findings:List<String>,
    val handshakeComplete:Int,
    val resets:Int,
    val retransmissions:Int,
    val dnsFailures:Int,
    val dhcpDoraComplete:Int,
    val handshakeFailures:Int = 0,
    val uniqueEndpoints:Int = 0,
    val topConversations:List<ConversationSummary> = emptyList(),
    val timeline:List<PacketTimelineEvent> = emptyList()
)

class PacketAnalysisEngine {
    private val protocols=listOf("ARP","ICMP","ICMPv6","TCP","UDP","DNS","DHCP","HTTP","HTTPS","TLS","SIP","RTP","IPv4","IPv6")

    fun parse(text:String):List<PacketRecord> = text.lines().asSequence().filter{it.isNotBlank()}.take(20_000).mapIndexed{idx,line ->
        val protocol=protocols.firstOrNull{Regex("\\b${Regex.escape(it)}\\b",RegexOption.IGNORE_CASE).containsMatchIn(line)}?:"UNKNOWN"
        val ips=Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b").findAll(line).map{it.value}.toList()
        val endpointMatch=Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}:(\\d{1,5})\\s*(?:→|->)\\s*(?:\\d{1,3}\\.){3}\\d{1,3}:(\\d{1,5})\\b").find(line)
        val portValues=endpointMatch?.let { listOfNotNull(it.groupValues.getOrNull(1)?.toIntOrNull(),it.groupValues.getOrNull(2)?.toIntOrNull()) }
            ?: (Regex("(?<!\\d)(\\d{1,5})\\s*(?:→|->|:)").findAll(line).mapNotNull{it.groupValues[1].toIntOrNull()}.toList()+
                Regex("(?:→|->|:|\\s)(\\d{1,5})(?!\\d)").findAll(line).mapNotNull{it.groupValues[1].toIntOrNull()}.toList()).distinct()
            .filter{it in 0..65535}.take(2)
        val flags=listOf("SYN","ACK","FIN","RST","PSH","URG").filter{line.contains(it,true)}.joinToString(",")
        PacketRecord(idx+1,parseTimestamp(line,idx),ips.getOrNull(0).orEmpty(),ips.getOrNull(1).orEmpty(),protocol,line.trim(),line.toByteArray().size,portValues.getOrNull(0),portValues.getOrNull(1),flags,
            Regex("vlan[ =:]+(\\d+)",RegexOption.IGNORE_CASE).find(line)?.groupValues?.getOrNull(1)?.toIntOrNull(),
            when{line.contains("IPv6",true)->6;line.contains("IPv4",true)||ips.isNotEmpty()->4;else->null})
    }.toList()

    fun summary(records:List<PacketRecord>):String=records.groupingBy{it.protocol}.eachCount().entries.sortedByDescending{it.value}.joinToString(", "){ "${it.key}: ${it.value}" }

    fun analyze(records:List<PacketRecord>):PacketAnalysisReport{
        val protocolCounts=records.groupingBy{it.protocol}.eachCount()
        val conversations=records.filter{it.source.isNotBlank()&&it.destination.isNotBlank()}.groupingBy{ConversationKey(it.source,it.destination,it.srcPort,it.dstPort,it.protocol)}.eachCount()
        val groupedTcp=records.filter{it.protocol=="TCP"}.groupBy{biKey(it)}
        val handshakeComplete=groupedTcp.values.count{p->
            p.any{it.tcpFlags.contains("SYN")&&!it.tcpFlags.contains("ACK")} &&
            p.any{it.tcpFlags.contains("SYN")&&it.tcpFlags.contains("ACK")} &&
            p.any{it.tcpFlags.contains("ACK")&&!it.tcpFlags.contains("SYN")}
        }
        val handshakeFailures=groupedTcp.values.count{p->p.any{it.tcpFlags.contains("SYN")&&!it.tcpFlags.contains("ACK")}&&!p.any{it.tcpFlags.contains("SYN")&&it.tcpFlags.contains("ACK")}}
        val resets=records.count{it.protocol=="TCP"&&it.tcpFlags.contains("RST")}
        val retransmissions=records.count{it.info.contains("Retransmission",true)||it.info.contains("Dup ACK",true)}
        val dnsFailures=records.count{it.protocol=="DNS"&&listOf("SERVFAIL","NXDOMAIN","REFUSED","timeout").any{f->it.info.contains(f,true)}}
        val dora= listOf("DISCOVER","OFFER","REQUEST","ACK").all{m->records.any{it.protocol=="DHCP"&&it.info.contains(m,true)}}
        val summaries=conversations.entries.sortedByDescending{it.value}.take(10).map{entry->
            val packets=records.filter{r->ConversationKey(r.source,r.destination,r.srcPort,r.dstPort,r.protocol)==entry.key}
            ConversationSummary(entry.key,entry.value,packets.count{it.tcpFlags.contains("RST")},packets.count{it.info.contains("Retransmission",true)||it.info.contains("Dup ACK",true)},packets.count{it.protocol=="DNS"&&listOf("SERVFAIL","NXDOMAIN","REFUSED").any{e->it.info.contains(e,true)}})
        }
        val endpoints=(records.flatMap{listOf(it.source,it.destination)}.filter{it.isNotBlank()}.toSet()).size
        return PacketAnalysisReport(records.size,protocolCounts,conversations,findings(records),handshakeComplete,resets,retransmissions,dnsFailures,if(dora)1 else 0,handshakeFailures,endpoints,summaries,timeline(records))
    }

    fun timeline(records:List<PacketRecord>,limit:Int=20):List<PacketTimelineEvent>{
        val out=mutableListOf<PacketTimelineEvent>()
        records.sortedBy{it.number}.forEach{r->
            when {
                r.protocol=="TCP"&&r.tcpFlags.contains("SYN")&&!r.tcpFlags.contains("ACK")->out+=PacketTimelineEvent(r.number,"TCP_SYN","${r.source}:${r.srcPort} → ${r.destination}:${r.dstPort}")
                r.protocol=="TCP"&&r.tcpFlags.contains("SYN")&&r.tcpFlags.contains("ACK")->out+=PacketTimelineEvent(r.number,"TCP_SYN_ACK","${r.destination}:${r.dstPort} accepted")
                r.protocol=="TCP"&&r.tcpFlags.contains("RST")->out+=PacketTimelineEvent(r.number,"TCP_RST","${r.source} reset session")
                r.protocol=="DNS"&&listOf("SERVFAIL","NXDOMAIN","REFUSED").any{e->r.info.contains(e,true)}->out+=PacketTimelineEvent(r.number,"DNS_ERROR",r.info.take(180))
                r.protocol=="DHCP"&&listOf("DISCOVER","OFFER","REQUEST","ACK").any{m->r.info.contains(m,true)}->out+=PacketTimelineEvent(r.number,"DHCP",r.info.take(180))
            }
            if(out.size>=limit.coerceIn(1,100))return out
        }
        return out
    }

    private fun biKey(r:PacketRecord):BidirectionalConversationKey {
        val left = "${r.source}:${r.srcPort ?: -1}"
        val right = "${r.destination}:${r.dstPort ?: -1}"
        return if (left <= right) BidirectionalConversationKey(r.source,r.destination,r.srcPort,r.dstPort,"TCP")
        else BidirectionalConversationKey(r.destination,r.source,r.dstPort,r.srcPort,"TCP")
    }

    fun findings(records:List<PacketRecord>):List<String>{
        val out=mutableListOf<String>()
        if(records.count{it.protocol=="TCP"}>=3&&records.any{it.info.contains("Retransmission",true)})out+="TCP retransmission/duplicate ACK evidence detected"
        if(records.any{it.protocol=="ARP"&&it.info.contains("is-at",true)})out+="ARP resolution observed"
        if(records.any{it.protocol=="DNS"})out+="DNS traffic observed"
        if(records.any{it.protocol=="DHCP"})out+="DHCP traffic observed"
        if(records.any{it.protocol=="TCP"&&it.tcpFlags.contains("RST")})out+="TCP reset observed"
        if(records.any{it.protocol=="TCP"&&it.info.contains("SYN",true)&&it.info.contains("ACK",true)})out+="TCP handshake evidence observed"
        if(records.any{it.protocol=="DNS"&&listOf("SERVFAIL","NXDOMAIN","REFUSED").any{e->it.info.contains(e,true)}})out+="DNS error response observed"
        val dora=listOf("DISCOVER","OFFER","REQUEST","ACK").all{m->records.any{it.protocol=="DHCP"&&it.info.contains(m,true)}}
        if(dora)out+="DHCP DORA exchange completed"
        return out.distinct()
    }

    private fun parseTimestamp(line:String,index:Int):String{
        val candidate=Regex("^\\[?(\\d+(?:\\.\\d+)?)\\]?").find(line)?.groupValues?.getOrNull(1)
        return candidate?:index.toString()
    }
}
