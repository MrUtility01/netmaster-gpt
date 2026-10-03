package com.example.netmaster.domain

data class TwinNode(val id:String,val name:String,val type:String,val ip:String?=null,val vlan:Int?=null)
data class TwinLink(val from:String,val to:String,val protocol:String="Ethernet",val up:Boolean=true)
data class TwinFault(val id:String,val title:String,val description:String,val target:String,val hint:String,val severity:String="MEDIUM")
data class TwinFix(val faultId:String,val title:String,val command:String,val expected:String)
data class TwinVerification(val passed:Boolean,val checks:List<String>,val remainingFault:String?=null,val score:Int)
data class TwinState(val nodes:List<TwinNode>,val links:List<TwinLink>,val activeFault:TwinFault?=null,val evidence:List<String> = emptyList(),val score:Int=0,val simulator:SimNetworkState?=null)

class DigitalTwinEngine(private val simulator:CommandSimulator=CommandSimulator()) {
    fun defaultState():TwinState{val s=simulator.defaultState();return TwinState(s.nodes,s.links,simulator=s)}

    fun faults(): List<TwinFault> = listOf(
        TwinFault("dhcp","DHCP Offer Failure","کلاینت Discover می‌فرستد اما Offer دریافت نمی‌کند.","edge","DHCP server/relay، VLAN و UDP 67/68 را بررسی کن.","HIGH"),
        TwinFault("dns","DNS Failure","کاربران IP دارند اما نام دامنه resolve نمی‌شود.","dns","ping DNS server و nslookup را بررسی کن.","HIGH"),
        TwinFault("vlan","VLAN/Trunk Mismatch","کاربر به Gateway دسترسی ندارد.","core","Access VLAN و allowed VLAN را بررسی کن.","HIGH"),
        TwinFault("route","Missing Route","شبکه مقصد از Edge قابل دسترسی نیست.","edge","Route/FIB و next-hop را بررسی کن.","HIGH"),
        TwinFault("firewall","Firewall Rule Order","ترافیک مجاز توسط Drop بالاتر قطع می‌شود.","edge","Rule order و counters را بررسی کن.","HIGH"),
        TwinFault("nat","NAT/PAT Failure","LAN به Internet route دارد ولی translation انجام نمی‌شود.","edge","srcnat/conntrack را بررسی کن.","MEDIUM"),
        TwinFault("ospf","OSPF Adjacency","Routeهای داخلی ناپدید شده‌اند.","edge","Neighbor state و area/hello را بررسی کن.","HIGH"),
        TwinFault("bgp","BGP Session","Prefixهای upstream دریافت نمی‌شوند.","edge","Session state و policy را بررسی کن.","HIGH"),
        TwinFault("stp","STP Topology Fault","Topology change و path instability دیده می‌شود.","core","Root/role/state و BPDU را بررسی کن.","HIGH"),
        TwinFault("mtu","TCP MSS / MTU Black Hole","Handshake works but larger transfers stall.","edge","Check interface MTU, PMTUD, MSS and blocked ICMP too-big messages.","HIGH"),
        TwinFault("qos","QoS Congestion","VoIP jitter/loss در زمان congestion بالا می‌رود.","core","Queue/marking و interface drops را بررسی کن.","MEDIUM")
    )

    fun fixes(): List<TwinFix> = listOf(
        TwinFix("dhcp","Restore DHCP","enable dhcp","DHCP running"),
        TwinFix("dns","Restore DNS","enable dns","DNS reachable"),
        TwinFix("vlan","Restore VLAN 10","restore vlan 10","VLAN 10 present"),
        TwinFix("route","Restore route","restore route 192.168.20.0/24","Route restored"),
        TwinFix("firewall","Restore firewall","enable firewall","Firewall healthy"),
        TwinFix("nat","Restore NAT","enable nat","NAT healthy"),
        TwinFix("ospf","Restore OSPF","enable ospf","OSPF healthy"),
        TwinFix("bgp","Restore BGP","enable bgp","BGP healthy"),
        TwinFix("stp","Restore STP","repair stp","STP healthy"),
        TwinFix("mtu","Restore MTU","restore mtu 1500","MTU restored"),
        TwinFix("qos","Restore QoS","enable qos","QoS healthy")
    )

    fun inject(state:TwinState,fault:TwinFault):TwinState{
        val sim=state.simulator?:simulator.defaultState()
        val modified=when(fault.id){
            "dhcp"->sim.copy(dhcpHealthy=false)
            "dns"->sim.copy(dnsHealthy=false)
            "vlan"->sim.copy(vlans=sim.vlans-10)
            "route"->sim.copy(routes=sim.routes.filterNot{it.prefix=="192.168.20.0/24"})
            "firewall"->sim.copy(firewallHealthy=false)
            "nat"->sim.copy(natHealthy=false)
            "ospf"->sim.copy(ospfHealthy=false,routes=sim.routes.filterNot{it.protocol=="ospf"})
            "bgp"->sim.copy(bgpHealthy=false,routes=sim.routes.filterNot{it.protocol=="bgp"})
            "stp"->sim.copy(stpHealthy=false)
            "mtu"->sim.copy(interfaces=sim.interfaces.map{it.copy(mtu=1400)})
            "qos"->sim.copy(qosHealthy=false)
            else->sim
        }
        return state.copy(activeFault=fault,evidence=emptyList(),score=0,simulator=modified)
    }

    fun repair(state:TwinState):TwinState{
        val fault=state.activeFault?:return state
        val sim=state.simulator?:simulator.defaultState()
        val fixed=when(fault.id){
            "dhcp"->sim.copy(dhcpHealthy=true)
            "dns"->sim.copy(dnsHealthy=true)
            "vlan"->sim.copy(vlans=sim.vlans+10)
            "route"->if(sim.routes.any{it.prefix=="192.168.20.0/24"})sim else sim.copy(routes=sim.routes+SimRoute("192.168.20.0/24",null,"connected"))
            "firewall"->sim.copy(firewallHealthy=true)
            "nat"->sim.copy(natHealthy=true)
            "ospf"->sim.copy(ospfHealthy=true,routes=if(sim.routes.any{it.protocol=="ospf"})sim.routes else sim.routes+SimRoute("192.168.40.0/24","10.0.0.2","ospf",110,20))
            "bgp"->sim.copy(bgpHealthy=true,routes=if(sim.routes.any{it.protocol=="bgp"})sim.routes else sim.routes+SimRoute("203.0.113.0/24","10.0.0.9","bgp",20,0))
            "stp"->sim.copy(stpHealthy=true)
            "mtu"->sim.copy(interfaces=sim.interfaces.map{it.copy(mtu=maxOf(it.mtu,1500))})
            "qos"->sim.copy(qosHealthy=true)
            else->sim
        }
        return state.copy(simulator=fixed)
    }

    fun recordEvidence(state:TwinState,evidence:String):TwinState{
        val items=(state.evidence+evidence.trim()).filter{it.isNotBlank()}.distinct().takeLast(30)
        return state.copy(evidence=items,score=(items.size*20).coerceAtMost(60))
    }

    fun verify(state:TwinState):TwinVerification{
        val sim=state.simulator?:return TwinVerification(false,listOf("No simulator state"),state.activeFault?.id,state.score)
        val fault=state.activeFault?:return TwinVerification(true,listOf("No active fault"),null,100)
        val checks=mutableListOf<String>()
        val healthy=when(fault.id){
            "dhcp"->{val ok=sim.dhcpHealthy;checks+="DHCP ${if(ok)"healthy" else "still unhealthy"}";ok}
            "dns"->{val ok=sim.dnsHealthy;checks+="DNS ${if(ok)"healthy" else "still unhealthy"}";ok}
            "vlan"->{val ok=10 in sim.vlans;checks+="VLAN 10 ${if(ok)"present" else "missing"}";ok}
            "route"->{val ok=sim.routes.any{it.prefix=="192.168.20.0/24"};checks+=if(ok)"Route restored" else "Route missing";ok}
            "firewall"->{val ok=sim.firewallHealthy;checks+="Firewall ${if(ok)"healthy" else "still unhealthy"}";ok}
            "nat"->{val ok=sim.natHealthy;checks+="NAT ${if(ok)"healthy" else "still unhealthy"}";ok}
            "ospf"->{val ok=sim.ospfHealthy&&sim.routes.any{it.protocol=="ospf"};checks+="OSPF ${if(ok)"healthy" else "still unhealthy"}";ok}
            "bgp"->{val ok=sim.bgpHealthy&&sim.routes.any{it.protocol=="bgp"};checks+="BGP ${if(ok)"healthy" else "still unhealthy"}";ok}
            "stp"->{val ok=sim.stpHealthy;checks+="STP ${if(ok)"healthy" else "still unhealthy"}";ok}
            "mtu"->{val ok=sim.interfaces.all{it.mtu>=1500};checks+=if(ok)"MTU restored" else "Reduced MTU remains";ok}
            "qos"->{val ok=sim.qosHealthy;checks+="QoS ${if(ok)"healthy" else "still unhealthy"}";ok}
            else->false
        }
        checks+="Evidence items: ${state.evidence.size}"
        val relevantEvidence = state.evidence.count { it.containsAnyEvidenceFor(fault.id) }
        checks += "Relevant evidence: $relevantEvidence/${state.evidence.size}"
        val passed=healthy&&relevantEvidence>=1
        val score=when{healthy&&relevantEvidence>=2->100;healthy&&relevantEvidence==1->85;else->state.score}
        return TwinVerification(passed,checks,if(healthy)null else fault.id,score)
    }

    private fun String.containsAnyEvidenceFor(faultId:String):Boolean {
        val s=lowercase()
        val keys=when(faultId){
            "dhcp"->listOf("dhcp","discover","offer","lease","renew")
            "dns"->listOf("dns","nslookup","dig","resolve")
            "vlan"->listOf("vlan","trunk","tag")
            "route"->listOf("route","routing","traceroute","fib")
            "firewall"->listOf("firewall","policy","deny","flow")
            "nat"->listOf("nat","translation","conntrack","session")
            "ospf"->listOf("ospf","neighbor","adjacency","lsdb")
            "bgp"->listOf("bgp","prefix","peer","session")
            "stp"->listOf("stp","root","bpdu","loop")
            "mtu"->listOf("mtu","mss","fragment","packet too big")
            "qos"->listOf("qos","jitter","queue","dscp","loss")
            else->listOf(faultId)
        }
        return keys.any { s.contains(it) }
    }

    fun complete(state:TwinState):TwinState{
        val v=verify(state)
        return if(v.passed) state.copy(activeFault=null,score=v.score) else state
    }

    fun reset(state:TwinState)=defaultState().copy(nodes=state.nodes,links=state.links)
}
