package com.example.netmaster.domain

import com.example.netmaster.data.Lesson
import com.example.netmaster.data.PacketRecord
import com.example.netmaster.data.ProgressEntity

/**
 * NetMaster Network Engineering OS.
 * Deterministic/offline reasoning layer: every conclusion is traceable to evidence,
 * a protocol/state transition, and a discriminating test. No device mutation is performed.
 */
class FieldEngineeringEngine {
    data class Evidence(val id:String, val type:String, val value:String, val source:String = "USER", val weight:Int = 50)
    data class Hypothesis(val title:String, val confidence:Int, val basis:List<String>, val falsifier:String, val nextTest:String)
    data class CausalHop(val from:String, val relation:String, val to:String, val evidence:String)
    data class Dependency(val node:String, val relation:String, val impact:Int, val why:String)
    data class Investigation(
        val symptom:String,
        val evidence:List<Evidence>,
        val hypotheses:List<Hypothesis>,
        val causalChain:List<CausalHop>,
        val firstDivergence:String,
        val discriminatingTests:List<String>,
        val relatedLessons:List<String>,
        val blastRadius:List<Dependency>
    )
    data class FaultCase(val id:String,val title:String,val layer:String,val symptom:String,val expectedEvidence:List<String>,val firstDivergence:String,val remediation:String,val rollback:String)
    data class Passport(val overall:Int,val competencies:List<Competency>,val strengths:List<String>,val gaps:List<String>)
    data class Competency(val name:String,val score:Int,val evidence:String,val next:String)

    fun investigate(symptom:String, rawEvidence:String, lessons:List<Lesson> = emptyList(), packets:List<PacketRecord> = emptyList()):Investigation {
        val combined = (symptom + " " + rawEvidence + " " + packets.joinToString(" "){ it.protocol + " " + it.info }).lowercase()
        val ev = parseEvidence(rawEvidence)
        val hypotheses = mutableListOf<Hypothesis>()
        val chain = mutableListOf<CausalHop>()
        fun add(t:String,c:Int,b:List<String>,f:String,test:String){hypotheses += Hypothesis(t,c,b,f,test)}
        val first = when {
            combined.contains("dhcp") || combined.contains("ip نمی") || combined.contains("ip نمیگیرد") -> {
                chain += CausalHop("Client Broadcast","DISCOVER","DHCP Server/Relay","DHCP Discover expected before Offer")
                if(combined.contains("discover") && !combined.contains("offer")) chain += CausalHop("DHCP Discover","missing","DHCP Offer","Offer is the first absent transaction stage")
                "DHCP transaction: Discover → Offer"
            }
            combined.contains("dns") || combined.contains("resolve") || combined.contains("نام دامنه") -> {
                chain += CausalHop("Application","query","Resolver","Name lookup starts at configured resolver")
                if(combined.contains("timeout") || combined.contains("servfail") || combined.contains("nxdomain")) chain += CausalHop("Resolver query","response failure","Application","Response semantics indicate the first divergence")
                "DNS transaction: application query → resolver response"
            }
            combined.contains("ospf") || combined.contains("neighbor") || combined.contains("adjacency") -> {
                chain += CausalHop("Interface","Hello","OSPF Neighbor","Hello establishes adjacency negotiation")
                if(combined.contains("exstart") || combined.contains("mtu")) chain += CausalHop("OSPF Neighbor","EXSTART/DBD","LSDB exchange","MTU/state mismatch can stop DBD progression")
                "OSPF adjacency: Hello → ExStart/Exchange → LSDB"
            }
            combined.contains("tcp") || combined.contains("retrans") || combined.contains("rst") -> {
                chain += CausalHop("TCP Sender","SYN/Data","Receiver","Sequence space and ACK progression define delivery")
                if(combined.contains("retrans")) chain += CausalHop("TCP Sender","retransmission","ACK progression","Missing/late ACK is the first delivery divergence")
                "TCP delivery: segment → ACK progression"
            }
            combined.contains("vlan") || combined.contains("trunk") || combined.contains("802.1q") -> {
                chain += CausalHop("Access Port","frame","Trunk","VLAN membership determines broadcast reachability")
                if(combined.contains("allowed") || combined.contains("native")) chain += CausalHop("Trunk","tag/allowlist","Destination VLAN","Tagging or allowlist can create the first divergence")
                "802.1Q path: access membership → tagged trunk → destination VLAN"
            }
            combined.contains("arp") || combined.contains("gateway") -> {
                chain += CausalHop("Host","ARP Request","Gateway MAC","Neighbor discovery precedes IPv4 forwarding")
                "Neighbor resolution: ARP request → reply → L3 forwarding"
            }
            else -> "Layered path: Link → Neighbor → Route/FIB → Transport → Service → Policy"
        }
        if(combined.contains("dhcp")) add("DHCP transaction break",86,listOf("DORA sequence is named or partially observed"),"Presence of Offer/ACK proving the transaction proceeds","Capture Discover/Offer/Request/Ack and inspect relay giaddr/options")
        if(combined.contains("dns")) add("Name-resolution failure",83,listOf("Resolver/response symptom is present"),"Successful query against the same resolver","Compare client resolver, recursive response, authoritative response, and TCP fallback")
        if(combined.contains("ospf")) add("OSPF adjacency/convergence fault",84,listOf("OSPF/neighbor state is implicated"),"Stable Full state with matching LSDB","Compare MTU, timers, auth, area, neighbor state, LSDB/RIB")
        if(combined.contains("tcp") || combined.contains("retrans")) add("TCP path loss/latency",81,listOf("TCP state or retransmission evidence"),"Clean ACK progression with no repeated sequence numbers","Compare both directions: sequence, ACK, RTT, window, MSS/MTU")
        if(combined.contains("vlan") || combined.contains("trunk")) add("VLAN/trunk segmentation fault",80,listOf("L2 segmentation terms are present"),"Frame reaches the expected VLAN at each hop","Check access VLAN, allowed list, native VLAN, STP and MAC learning")
        if(hypotheses.isEmpty()) add("Layered network-path fault",58,listOf("No protocol-specific discriminator yet"),"A clean end-to-end test across all layers","Run low-risk tests from link → ARP/ND → route/FIB → service → policy")

        val tags = hypotheses.flatMap{it.title.lowercase().split(" ","/","-")}.toSet()
        val related = lessons.filter{l -> tags.any{t -> t.length>3 && (l.title.lowercase().contains(t) || l.technical.lowercase().contains(t) || l.tags.any{tag->tag.lowercase().contains(t)})}}.map{it.title}.distinct().take(10)
        val blast = dependencyReport(combined).take(8)
        val tests = hypotheses.distinctBy{it.nextTest}.take(6).map{it.nextTest}
        return Investigation(symptom,ev,hypotheses.sortedByDescending{it.confidence},chain,first,tests,related,blast)
    }

    fun parseEvidence(raw:String):List<Evidence> = raw.lines().filter{it.isNotBlank()}.mapIndexed{idx,line ->
        val type = when {
            Regex("pcap|packet|tcpdump|wireshark|arp|syn|ack|dns",RegexOption.IGNORE_CASE).containsMatchIn(line) -> "PACKET"
            Regex("show |get |journal|event|log|error|warn|critical",RegexOption.IGNORE_CASE).containsMatchIn(line) -> "LOG"
            Regex("route|vlan|trunk|acl|firewall|nat|ospf|bgp|config",RegexOption.IGNORE_CASE).containsMatchIn(line) -> "CONFIG"
            Regex("cpu|memory|latency|rtt|loss|drop|jitter|pps|iops",RegexOption.IGNORE_CASE).containsMatchIn(line) -> "METRIC"
            else -> "OBSERVATION"
        }
        Evidence("E${idx+1}",type,line.trim(),"USER",weight=when(type){"PACKET"->90;"CONFIG"->85;"LOG"->80;"METRIC"->75;else->55})
    }

    fun dependencyReport(text:String):List<Dependency> {
        val t=text.lowercase(); val out=mutableListOf<Dependency>()
        fun add(n:String,r:String,i:Int,w:String){out += Dependency(n,r,i,w)}
        if(t.contains("dns")||t.contains("resolve")){add("Application","depends-on DNS",92,"Name resolution affects application connection setup");add("AD/Kerberos","often depends on DNS",84,"Directory authentication commonly requires accurate service discovery")}
        if(t.contains("dhcp")||t.contains("ip نمی")){add("Host reachability","depends-on address assignment",90,"Address, gateway and DNS options come from the DHCP path");add("DNS","may be degraded",72,"Clients without valid resolver options cannot resolve names")}
        if(t.contains("ntp")||t.contains("time")||t.contains("kerberos")){add("Kerberos","depends-on time",95,"Ticket validation is time-sensitive")}
        if(t.contains("vlan")||t.contains("trunk")){add("DHCP Relay","depends-on VLAN reachability",86,"Broadcast domain placement determines relay reachability");add("Inter-VLAN Routing","depends-on VLAN state",82,"A missing VLAN/tag blocks routed reachability")}
        if(t.contains("ospf")||t.contains("route")){add("Applications","depends-on routing",88,"Broken RIB/FIB prevents traffic delivery");add("Monitoring","may lose reachability",71,"Management traffic follows the routing path")}
        if(t.contains("firewall")||t.contains("acl")||t.contains("policy")){add("Services","depends-on policy",87,"Stateful/ordered policy can drop otherwise valid traffic");add("Observability","depends-on logging",63,"Without counters/logs the cause is harder to distinguish")}
        if(t.contains("nat")){add("Internet egress","depends-on NAT/conntrack",94,"Private addressing needs a valid translation path for Internet return traffic")}
        return out.sortedByDescending{it.impact}
    }

    fun faultCatalog():List<FaultCase> = listOf(
        FaultCase("eng-dhcp-offer","DHCP Offer Missing","L3/Service","Clients broadcast Discover but never receive an Offer",listOf("Discover observed","Offer absent","giaddr/interface"),"Discover → Offer","Verify relay/VLAN/scope and UDP 67/68 reachability","Restore previous relay/VLAN policy"),
        FaultCase("eng-dns-servfail","DNS SERVFAIL","Service","Host has IP but name resolution intermittently fails",listOf("SERVFAIL","resolver logs","authoritative query"),"Resolver → authoritative response","Inspect recursion, delegation and upstream reachability","Restore resolver policy/cache configuration"),
        FaultCase("eng-ospf-mtu","OSPF MTU Mismatch","Control Plane","Neighbors stop before Full",listOf("ExStart/Exchange","DBD","MTU values"),"ExStart → DBD exchange","Align effective MTU or explicitly document exception","Reapply previous interface MTU"),
        FaultCase("eng-vlan-allow","Trunk VLAN Omission","L2","Only some VLANs traverse an uplink",listOf("allowed VLAN list","tagged frame missing","MAC learning"),"Trunk → allowed VLAN","Add intended VLAN to trunk allowlist after review","Restore prior allowlist"),
        FaultCase("eng-tcp-mss","TCP MSS/MTU Black Hole","Transport","Handshake works but larger transfers stall",listOf("SYN MSS","DF/ICMP","retransmissions"),"Large segment → missing ACK","Validate PMTUD/MSS and intermediate filtering","Restore prior MSS/MTU policy"),
        FaultCase("eng-fw-order","Firewall Rule Shadowing","Policy","Expected traffic is silently dropped",listOf("rule counters","ordering","state table"),"Packet → higher-priority deny","Move/limit rule with documented change scope","Restore previous rule ordering"),
        FaultCase("eng-nat-state","NAT Conntrack Asymmetry","Policy/Data Plane","Outbound sessions create but replies do not return",listOf("conntrack","translated tuple","return route"),"Translated flow → return path","Check srcnat, state table and asymmetric routing","Rollback NAT/policy change"),
        FaultCase("eng-stp-root","STP Root Change","L2 Control Plane","Unexpected path change and topology churn",listOf("BPDU","root ID","port roles","TCN"),"Expected root → unexpected root/path","Validate bridge priority and edge safeguards","Restore intended root priority")
    )

    fun passport(progress:Map<String,ProgressEntity>,incidentsResolved:Int,captureCount:Int,lessonCatalog:List<Lesson>):Passport {
        val total=lessonCatalog.size.coerceAtLeast(1); val completed=progress.values.count{it.completed};val mastered=progress.values.count{it.mastery=="MASTERED"}
        val learning=((completed*70.0/total)+(mastered*30.0/total)).roundToInt().coerceIn(0,100)
        val op=(incidentsResolved*12).coerceAtMost(100);val packet=(captureCount*15).coerceAtMost(100)
        val comps=listOf(
            Competency("Core Networking",learning,"Completion + mastery across 850-core curriculum","Close remaining protocol gaps"),
            Competency("Troubleshooting",((learning*0.55)+(op*0.45)).roundToInt(),"Mastery combined with resolved incidents","Run more break/fix investigations"),
            Competency("Packet Analysis",((learning*0.45)+(packet*0.55)).roundToInt(),"Lesson mastery plus capture usage","Analyze bidirectional flows and first divergence"),
            Competency("Operational Reasoning",((learning*0.35)+(op*0.65)).roundToInt(),"Incident closure evidence","Record discriminating tests and rollback paths"),
            Competency("Engineering Evidence",((learning*0.40)+(packet*0.30)+(op*0.30)).roundToInt(),"Packet + incident evidence chains","Increase hard evidence density")
        )
        val overall=comps.map{it.score}.average().roundToInt();val strengths=comps.sortedByDescending{it.score}.take(2).map{it.name};val gaps=comps.sortedBy{it.score}.take(2).map{it.name}
        return Passport(overall,comps,strengths,gaps)
    }

    private fun Double.roundToInt():Int = kotlin.math.round(this).toInt()
}
