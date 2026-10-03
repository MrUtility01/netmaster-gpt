package com.example.netmaster.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable private data class TwinNodeDto(val id:String,val name:String,val type:String,val ip:String?=null,val vlan:Int?=null)
@Serializable private data class TwinLinkDto(val from:String,val to:String,val protocol:String="Ethernet",val up:Boolean=true)
@Serializable private data class SimInterfaceDto(val name:String,val up:Boolean,val vlan:Int?,val mtu:Int,val addresses:List<String>)
@Serializable private data class SimRouteDto(val prefix:String,val nextHop:String?,val protocol:String,val distance:Int,val metric:Int)
@Serializable private data class SimStateDto(val nodes:List<TwinNodeDto>,val links:List<TwinLinkDto>,val vlans:List<Int>,val routes:List<SimRouteDto>,val arp:Map<String,String>,val macTable:Map<String,String>,val interfaces:List<SimInterfaceDto>,val dnsHealthy:Boolean,val dhcpHealthy:Boolean,val natHealthy:Boolean,val firewallHealthy:Boolean,val ospfHealthy:Boolean,val bgpHealthy:Boolean,val stpHealthy:Boolean,val qosHealthy:Boolean)

object SimulatorCodec {
    private val json=Json{prettyPrint=false}
    fun encode(state:SimNetworkState):String=json.encodeToString(SimStateDto(
        state.nodes.map{TwinNodeDto(it.id,it.name,it.type,it.ip,it.vlan)},state.links.map{TwinLinkDto(it.from,it.to,it.protocol,it.up)},state.vlans.sorted(),
        state.routes.map{SimRouteDto(it.prefix,it.nextHop,it.protocol,it.distance,it.metric)},state.arp,state.macTable,state.interfaces.map{SimInterfaceDto(it.name,it.up,it.vlan,it.mtu,it.addresses)},
        state.dnsHealthy,state.dhcpHealthy,state.natHealthy,state.firewallHealthy,state.ospfHealthy,state.bgpHealthy,state.stpHealthy,state.qosHealthy))
    fun decode(value:String):SimNetworkState {
        val x=json.decodeFromString<SimStateDto>(value)
        return SimNetworkState(x.nodes.map{TwinNode(it.id,it.name,it.type,it.ip,it.vlan)},x.links.map{TwinLink(it.from,it.to,it.protocol,it.up)},x.vlans.toSet(),x.routes.map{SimRoute(it.prefix,it.nextHop,it.protocol,it.distance,it.metric)},x.arp,x.macTable,x.interfaces.map{SimInterface(it.name,it.up,it.vlan,it.mtu,it.addresses)},x.dnsHealthy,x.dhcpHealthy,x.natHealthy,x.firewallHealthy,x.ospfHealthy,x.bgpHealthy,x.stpHealthy,x.qosHealthy)
    }
}
