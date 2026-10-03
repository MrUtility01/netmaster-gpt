package com.example.netmaster.domain

import com.example.netmaster.ai.AiHypothesis
import com.example.netmaster.data.EngineeringScenario
import com.example.netmaster.data.PacketRecord

data class CoachStep(val id:String,val title:String,val reason:String,val command:String,val expected:String,val risk:String="LOW")
data class CoachSession(
    val symptom:String,val hypotheses:List<AiHypothesis>,val steps:List<CoachStep>,val currentIndex:Int=0,
    val evidence:List<String> = emptyList(),val findings:List<String> = emptyList(),val status:String="ACTIVE",val scenarioId:String?=null
)

class TroubleshootingCoach(private val simulator:CommandSimulator=CommandSimulator(),private val scenarioEngine:ScenarioEngine?=null) {
    fun start(symptom:String,twin:SimNetworkState,packets:List<PacketRecord> = emptyList()):CoachSession{
        val hit=scenarioEngine?.retrieve(symptom,1)?.firstOrNull()?.scenario
        return if(hit!=null)fromScenario(symptom,hit,twin,packets) else generic(symptom,packets)
    }
    private fun fromScenario(symptom:String,s:EngineeringScenario,twin:SimNetworkState,packets:List<PacketRecord>):CoachSession{
        val healthBonus = if (twin.dnsHealthy && twin.firewallHealthy && twin.natHealthy) 0 else 8
        val hs=s.hypotheses.mapIndexed{idx,h->AiHypothesis(h,(82-idx*12+healthBonus).coerceAtMost(95),s.evidence.take(4))}.ifEmpty{listOf(AiHypothesis(s.domain,60+healthBonus,s.evidence))}
        val steps=s.tests.take(8).mapIndexed{i,test->CoachStep("${s.id}-$i","Evidence Test ${i+1}","این Test برای محدودکردن فرضیه‌های ${s.domain} انتخاب شده است.",test,"Evidence compatible with scenario ${s.id}")}.toMutableList()
        if(packets.isNotEmpty())steps.add(CoachStep("${s.id}-pcap","Capture Correlation","Capture موجود را با Symptom تطبیق بده.","filter: tcp or dns","Correlation found","LOW"))
        return CoachSession(symptom,hs,steps,scenarioId=s.id)
    }
    private fun generic(symptom:String,packets:List<PacketRecord>):CoachSession{
        val steps=mutableListOf(CoachStep("base-1","Interface baseline","اول Link/Interface را ثابت کن.","show interfaces status","Expected interfaces UP"),CoachStep("base-2","ARP/Neighbor","Resolution لایه 2/3 را بررسی کن.","show arp","Gateway/peer MAC present"),CoachStep("base-3","Route/FIB","تصمیم Routing را بررسی کن.","show ip route","Expected route exists"),CoachStep("base-4","Service","Service dependency را Probe کن.","nslookup example.com","Name/Address returned"))
        if(packets.isNotEmpty())steps.add(CoachStep("pcap-1","Capture","Packet evidence را با Symptom correlate کن.","filter: tcp or dns","Packets match the symptom"))
        return CoachSession(symptom,listOf(AiHypothesis("Layered network path",55,listOf("interface","arp/nd","route","service"))),steps)
    }
    fun executeNext(session:CoachSession,state:SimNetworkState):Pair<CoachSession,SimulatedCommandResult?>{
        if(session.currentIndex>=session.steps.size)return session.copy(status="WAITING_FOR_REVIEW") to null
        val step=session.steps[session.currentIndex];val command=step.command.removePrefix("filter: ").let{if(it=="tcp or dns")"show interfaces status" else it}
        val result=simulator.execute(state,command);val ev="${step.id} | ${result.category} | ${result.output.lineSequence().take(3).joinToString(" / ")}"
        val findings=mutableListOf<String>();if(!result.success)findings+="Command unsupported in simulator: $command";if(result.output.contains("timed out",true)||result.output.contains("unreachable",true))findings+="Negative evidence: $command";if(result.output.contains("FAIL",true)||result.output.contains("degraded",true))findings+="Service/state degradation observed"
        val twinSignal = if (state.dnsHealthy && state.firewallHealthy && state.natHealthy && state.ospfHealthy && state.bgpHealthy && state.stpHealthy && state.qosHealthy) "healthy-baseline" else "fault-present"
        findings += "Twin state: $twinSignal"
        val next=session.copy(currentIndex=session.currentIndex+1,evidence=(session.evidence+ev).takeLast(30),findings=(session.findings+findings).distinct())
        return next to result
    }
}
