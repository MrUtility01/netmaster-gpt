package com.example.netmaster.domain

import com.example.netmaster.ai.AiHypothesis
import com.example.netmaster.data.Lesson

/** Offline correlation layer: connects evidence, retrieval, packet state and failure hypotheses. */
class EngineeringIntelligenceEngine(
    private val rag: com.example.netmaster.search.RagEngine = com.example.netmaster.search.RagEngine()
) {
    data class Evidence(val type:String,val value:String,val source:String="user")
    data class Finding(val title:String,val confidence:Int,val reason:String,val tests:List<String>)
    data class Investigation(val symptom:String,val findings:List<Finding>,val firstDivergence:String?,val relatedLessons:List<String>)

    fun investigate(symptom:String, lessons:List<Lesson>, packets:List<com.example.netmaster.data.PacketRecord> = emptyList(), evidence:List<Evidence> = emptyList()):Investigation {
        rag.buildIndex(lessons)
        val hits=rag.retrieve(symptom,8)
        val text=(packets.joinToString(" "){it.protocol+" "+it.info}+" "+evidence.joinToString(" "){it.value}).lowercase()
        val findings=mutableListOf<Finding>()
        fun add(title:String,confidence:Int,reason:String,vararg tests:String){findings+=Finding(title,confidence,reason,tests.toList())}
        when {
            text.contains("retransmission") || symptom.contains("retransmission",true) -> add("TCP loss / path quality",82,"TCP evidence shows loss or recovery behavior","capture both directions","inspect sequence/ACK","check RTT and MTU")
            text.contains("dhcp") || symptom.contains("dhcp",true) || symptom.contains("ip address",true) -> add("DHCP transaction break",78,"The failure maps to the DORA path","capture Discover/Offer/Request/Ack","verify VLAN","verify relay giaddr")
            text.contains("dns") || symptom.contains("dns",true) -> add("DNS resolution path",76,"The symptom is consistent with recursive/authoritative or transport failure","query resolver","query authoritative","test TCP fallback")
            text.contains("ospf") || symptom.contains("ospf",true) -> add("OSPF adjacency/convergence",80,"The symptom maps to neighbor state or LSDB/RIB convergence","check MTU/timers/auth","inspect neighbor state","compare LSDB/RIB")
            text.contains("vlan") || symptom.contains("vlan",true) || symptom.contains("trunk",true) -> add("802.1Q / switching path",79,"The symptom maps to access/trunk/tagging state","check access VLAN","check allowed VLANs","inspect MAC/STP")
            else -> add("Layered path fault",55,"No single protocol-specific discriminator is present yet","interface/link","ARP/ND","route/FIB","service/policy")
        }
        val divergence=when {
            text.contains("arp") && !text.contains("tcp") -> "L2/L3 neighbor resolution before transport"
            text.contains("retransmission") -> "TCP delivery/ACK progression"
            text.contains("dns") -> "Name-resolution transaction"
            text.contains("ospf") -> "Control-plane adjacency/convergence"
            else -> null
        }
        return Investigation(symptom,findings.sortedByDescending{it.confidence},divergence,hits.map{it.title}.distinct().take(8))
    }

    fun toAiHypotheses(inv:Investigation)=inv.findings.map{AiHypothesis(it.title,it.confidence,it.tests)}
}
