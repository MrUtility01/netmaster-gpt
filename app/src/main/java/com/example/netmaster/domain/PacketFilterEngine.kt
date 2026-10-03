package com.example.netmaster.domain

import com.example.netmaster.data.PacketRecord

/** A deliberately focused Wireshark-like display-filter grammar. */
class PacketFilterEngine {
    data class FilterResult(val records:List<PacketRecord>,val error:String?)
    private sealed interface Expr
    private data class Atom(val field:String?,val op:String?,val value:String):Expr
    private data class And(val left:Expr,val right:Expr):Expr
    private data class Or(val left:Expr,val right:Expr):Expr
    private data class Not(val inner:Expr):Expr

    fun filter(records:List<PacketRecord>,expression:String):FilterResult {
        if(expression.isBlank())return FilterResult(records,null)
        return try{val tokens=lex(expression);val parser=Parser(tokens);val expr=parser.parse();FilterResult(records.filter{eval(expr,it)},null)}catch(e:IllegalArgumentException){FilterResult(emptyList(),e.message ?: "Invalid filter")}
    }

    private fun lex(s:String): List<String>{
        val out=mutableListOf<String>();var i=0
        while(i<s.length){
            when(s[i]){
                ' ','\t','\r','\n'->i++
                '('-> {out+="(";i++}
                ')'-> {out+=")";i++}
                '=','!' -> {if(i+1<s.length&&s[i+1]=='='){out+=s.substring(i,i+2);i+=2}else{out+=s[i].toString();i++}}
                '"','\''->{val q=s[i];i++;val start=i;while(i<s.length&&s[i]!=q)i++;if(i>=s.length)throw IllegalArgumentException("Unclosed quote");out+=s.substring(start,i);i++}
                else->{val start=i;while(i<s.length&&!s[i].isWhitespace()&&s[i]!='('&&s[i]!=')')i++;out+=s.substring(start,i)}
            }
        }
        return out
    }

    private inner class Parser(private val t:List<String>){var i=0
        fun parse():Expr{val e=or();if(i<t.size)throw IllegalArgumentException("Unexpected token: ${t[i]}");return e}
        private fun or():Expr{var e=and();while(i<t.size&&t[i].equals("or",true)){i++;e=Or(e,and())};return e}
        private fun and():Expr{var e=unary();while(i<t.size&&t[i].equals("and",true)){i++;e=And(e,unary())};return e}
        private fun unary():Expr{if(i<t.size&&(t[i].equals("not",true)||t[i]=="!")){i++;return Not(unary())};if(i<t.size&&t[i]=="("){i++;val e=or();if(i>=t.size||t[i]!=")")throw IllegalArgumentException("Missing ')' in filter");i++;return e};return atom()}
        private fun atom():Expr{
            if(i>=t.size)throw IllegalArgumentException("Missing filter expression")
            val first=t[i++]
            val ops=setOf("=","==","!=","contains","contains_ci")
            return if(i<t.size&&t[i].lowercase() in ops){val op=t[i++].lowercase();if(i>=t.size)throw IllegalArgumentException("Missing value for $first");Atom(first,op,t[i++])}
            else Atom(null,null,first)
        }
    }

    private fun eval(e:Expr,r:PacketRecord):Boolean=when(e){
        is And->eval(e.left,r)&&eval(e.right,r)
        is Or->eval(e.left,r)||eval(e.right,r)
        is Not->!eval(e.inner,r)
        is Atom->match(r,e)
    }

    private fun match(r:PacketRecord,a:Atom):Boolean{
        if(a.field==null)return protocolMatches(r,a.value)
        val field=a.field.lowercase();val value=a.value.trim('"','\'')
        val actual=fieldValue(r,field)
        return when(a.op){
            "contains"->actual.any{it.contains(value,true)}
            "contains_ci"->actual.any{it.lowercase().contains(value.lowercase())}
            "=" ,"=="->actual.any{it.equals(value,true)} || portEquals(field,r,value) || flagEquals(field,r,value)
            "!="->!(actual.any{it.equals(value,true)} || portEquals(field,r,value) || flagEquals(field,r,value))
            else->false
        }
    }

    private fun protocolMatches(r:PacketRecord,value:String)=when(value.lowercase()){
        "ip","ipv4"->r.ipVersion==4||r.protocol=="IPv4"
        "ipv6"->r.ipVersion==6||r.protocol=="IPv6"
        "tcp"->r.protocol.equals("TCP",true)
        "udp"->r.protocol.equals("UDP",true)
        "icmp"->r.protocol.equals("ICMP",true)||r.protocol.equals("ICMPv6",true)
        "arp"->r.protocol.equals("ARP",true)
        "dns"->r.protocol.equals("DNS",true)||r.info.contains("DNS",true)
        "dhcp"->r.protocol.equals("DHCP",true)||r.info.contains("DHCP",true)
        "http"->r.info.contains("HTTP",true)
        "https","tls"->r.info.contains("HTTPS",true)||r.info.contains("TLS",true)
        "sip"->r.info.contains("SIP",true)
        "rtp"->r.info.contains("RTP",true)
        "vlan"->r.vlanId!=null
        else->r.protocol.equals(value,true)||r.info.contains(value,true)
    }

    private fun fieldValue(r:PacketRecord,f:String): List<String> = when(f){
        "protocol","ip.proto","frame.protocols"->listOf(r.protocol)
        "ip.src","ip.source"->listOf(r.source)
        "ip.dst","ip.destination"->listOf(r.destination)
        "ip.addr"->listOf(r.source,r.destination)
        "tcp.port","tcp.srcport","tcp.dstport"->listOfNotNull(r.srcPort,r.dstPort).map(Int::toString)
        "udp.port","udp.srcport","udp.dstport"->listOfNotNull(r.srcPort,r.dstPort).map(Int::toString)
        "tcp.stream"->listOfNotNull(r.streamId).map(Int::toString)
        "tcp.flags"->listOf(r.tcpFlags)
        "tcp.flags.syn"->listOf(if("SYN" in r.tcpFlags)"1" else "0")
        "tcp.flags.ack"->listOf(if("ACK" in r.tcpFlags)"1" else "0")
        "tcp.flags.fin"->listOf(if("FIN" in r.tcpFlags)"1" else "0")
        "tcp.flags.reset","tcp.flags.rst"->listOf(if("RST" in r.tcpFlags)"1" else "0")
        "frame.number","number"->listOf(r.number.toString())
        "frame.len","frame.length","length"->listOf(r.length.toString())
        "frame.time"->listOf(r.timestamp)
        "frame.info","info"->listOf(r.info)
        "vlan.id"->listOfNotNull(r.vlanId).map(Int::toString)
        "eth.addr","eth.src","eth.dst"->listOf(r.source,r.destination)
        else->listOf(r.info)
    }
    private fun portEquals(f:String,r:PacketRecord,v:String):Boolean=when(f){"tcp.port","udp.port"->listOfNotNull(r.srcPort,r.dstPort).any{it.toString()==v&&((f.startsWith("tcp")&&r.protocol=="TCP")||(f.startsWith("udp")&&r.protocol=="UDP"))};"tcp.srcport"->r.protocol=="TCP"&&r.srcPort?.toString()==v;"tcp.dstport"->r.protocol=="TCP"&&r.dstPort?.toString()==v;"udp.srcport"->r.protocol=="UDP"&&r.srcPort?.toString()==v;"udp.dstport"->r.protocol=="UDP"&&r.dstPort?.toString()==v;else->false}
    private fun flagEquals(f:String,r:PacketRecord,v:String)=f.startsWith("tcp.flags")&&(if(v=="1")fieldValue(r,f).firstOrNull()=="1" else false)
}
