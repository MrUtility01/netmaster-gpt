package com.example.netmaster.domain

import com.example.netmaster.data.ConfigDiffLine
import java.security.MessageDigest

data class ConfigDiffSummary(
    val added: Int,
    val removed: Int,
    val unchanged: Int,
    val changedBlocks: Int,
    val riskFlags: List<String>,
    val riskScore: Int = 0,
    val sections: List<String> = emptyList(),
    val semanticChanges: List<String> = emptyList()
)

data class ConfigRisk(
    val level: String,
    val category: String,
    val line: String,
    val reason: String
)

class ConfigDiffEngine {
    fun diff(old: String,new: String):List<ConfigDiffLine>{
        val a=normalizeLines(old);val b=normalizeLines(new)
        if(a==b)return a.mapIndexed{i,line->ConfigDiffLine(" ",line,i+1)}
        val lcs=Array(a.size+1){IntArray(b.size+1)}
        for(i in a.indices.reversed())for(j in b.indices.reversed()){
            lcs[i][j]=if(a[i]==b[j])1+lcs[i+1][j+1]else maxOf(lcs[i+1][j],lcs[i][j+1])
        }
        val out=mutableListOf<ConfigDiffLine>();var i=0;var j=0
        while(i<a.size&&j<b.size){
            when{
                a[i]==b[j]->{out+=ConfigDiffLine(" ",a[i],i+1);i++;j++}
                lcs[i+1][j]>=lcs[i][j+1]->{out+=ConfigDiffLine("-",a[i],i+1);i++}
                else->{out+=ConfigDiffLine("+",b[j],j+1);j++}
            }
        }
        while(i<a.size){out+=ConfigDiffLine("-",a[i],i+1);i++}
        while(j<b.size){out+=ConfigDiffLine("+",b[j],j+1);j++}
        return out
    }

    fun risks(old:String,new:String):List<ConfigRisk>{
        return diff(old,new).filter { it.type!=" " }.mapNotNull { line ->
            val x=line.line.trim().lowercase()
            when {
                listOf("shutdown","no ip","no route","delete","remove","deny","drop","disable").any { x.contains(it) } -> ConfigRisk("HIGH","disruptive",line.line,"May remove reachability, policy access or a service")
                listOf("password","secret","community","private-key","certificate","token").any { x.contains(it) } -> ConfigRisk("HIGH","credential",line.line,"Touches credentials or security material")
                listOf("route","ospf","bgp","stp","vlan","trunk","mtu","nat").any { x.contains(it) } -> ConfigRisk("MEDIUM","network-state",line.line,"Can change forwarding or convergence behavior")
                line.type=="+" -> ConfigRisk("LOW","additive",line.line,"Additive configuration; verify scope and order")
                else -> null
            }
        }
    }

    fun summarize(old:String,new:String):ConfigDiffSummary{
        val d=diff(old,new)
        var blocks=0;var inBlock=false
        for(line in d){
            val changed=line.type!=" "
            if(changed&&!inBlock){blocks++;inBlock=true}
            if(!changed)inBlock=false
        }
        val risks=risks(old,new)
        return ConfigDiffSummary(
            added=d.count{it.type=="+"},
            removed=d.count{it.type=="-"},
            unchanged=d.count{it.type==" "},
            changedBlocks=blocks,
            riskFlags=risks.map { "${it.level}: ${it.line}" }.distinct().take(12),
            riskScore=risks.fold(0) { acc, r -> acc + when(r.level){"HIGH"->3;"MEDIUM"->2;else->1} }.coerceAtMost(20),
            sections=extractSections(d).take(20),
            semanticChanges=semanticChanges(d).take(12)
        )
    }

    fun fingerprint(text:String):String=MessageDigest.getInstance("SHA-256")
        .digest(normalizeLines(text).filter{it.isNotBlank()}.joinToString("\n").toByteArray(Charsets.UTF_8))
        .joinToString(""){ "%02x".format(it) }

    private fun extractSections(lines:List<ConfigDiffLine>):List<String>{
        val out=mutableListOf<String>();var current="global"
        for(line in lines){
            val s=line.line.trim()
            if(s.matches(Regex("^(interface|router|vlan|policy|config|hostname|system|/ip|/interface|/routing)[\\s/].*",RegexOption.IGNORE_CASE)))current=s
            if(line.type!=" "&&current.isNotBlank())out+=current
        }
        return out.distinct()
    }

    private fun semanticChanges(lines:List<ConfigDiffLine>):List<String>{
        val plus=lines.filter{it.type=="+"}.map{it.line.lowercase()}
        val minus=lines.filter{it.type=="-"}.map{it.line.lowercase()}
        val out=mutableListOf<String>()
        if(plus.any{it.contains("permit")||it.contains("allow")})out+="Access rule added"
        if(minus.any{it.contains("permit")||it.contains("allow")})out+="Access rule removed"
        if(plus.any{it.contains("deny")||it.contains("drop")})out+="Drop rule added"
        if(minus.any{it.contains("deny")||it.contains("drop")})out+="Drop rule removed"
        if(plus.any{it.contains("route")})out+="Route-related change added"
        if(minus.any{it.contains("route")})out+="Route-related change removed"
        if(plus.any{it.contains("vlan")||it.contains("trunk")})out+="Layer-2 segmentation change added"
        if(minus.any{it.contains("vlan")||it.contains("trunk")})out+="Layer-2 segmentation change removed"
        if(plus.any{it.contains("nat")||it.contains("srcnat")||it.contains("dstnat")})out+="NAT/translation change added"
        if(minus.any{it.contains("nat")||it.contains("srcnat")||it.contains("dstnat")})out+="NAT/translation change removed"
        if(plus.any{it.contains("certificate")||it.contains("secret")||it.contains("password")})out+="Security material change detected"
        return out.distinct()
    }

    private fun normalizeLines(text:String)=text.replace("\r\n","\n").replace('\r','\n').lines()
}
