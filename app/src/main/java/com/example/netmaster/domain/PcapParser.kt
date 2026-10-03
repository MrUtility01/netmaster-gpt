package com.example.netmaster.domain

import com.example.netmaster.data.PacketRecord
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Focused offline PCAP/PCAPNG parser; not a replacement for Wireshark's complete dissector stack. */
class PcapParser {
    fun parse(bytes:ByteArray,maxPackets:Int=10000):List<PacketRecord>{
        require(bytes.size<=80*1024*1024){"Capture too large; limit is 80 MiB"}
        return when { isClassic(bytes)->parseClassic(bytes,maxPackets); isPcapNg(bytes)->parsePcapNg(bytes,maxPackets); else->emptyList() }
    }
    private fun isClassic(b:ByteArray)=b.size>=4&&setOf(0xa1b2c3d4L,0xa1b23c4dL,0xd4c3b2a1L,0x4d3cb2a1L).contains(readU32(b,0,ByteOrder.BIG_ENDIAN))
    private fun isPcapNg(b:ByteArray)=b.size>=4&&readU32(b,0,ByteOrder.BIG_ENDIAN)==0x0A0D0D0AL

    private fun parseClassic(bytes:ByteArray,maxPackets:Int):List<PacketRecord>{
        if(bytes.size<24)return emptyList()
        val magic=readU32(bytes,0,ByteOrder.BIG_ENDIAN)
        val order=when(magic){0xa1b2c3d4L,0xa1b23c4dL->ByteOrder.BIG_ENDIAN;0xd4c3b2a1L,0x4d3cb2a1L->ByteOrder.LITTLE_ENDIAN;else->return emptyList()}
        val out=mutableListOf<PacketRecord>();var off=24;var n=1
        while(off+16<=bytes.size&&out.size<maxPackets){
            val tsSec=readU32(bytes,off,order);val tsFrac=readU32(bytes,off+4,order);val incl=readU32(bytes,off+8,order).toInt();val orig=readU32(bytes,off+12,order).toInt();off+=16
            if(incl<0||orig<0||off+incl>bytes.size)break
            out+=decodeFrame(bytes,off,incl,n,"$tsSec.$tsFrac");off+=incl;n++
        }
        return out
    }

    private fun parsePcapNg(bytes:ByteArray,maxPackets:Int):List<PacketRecord>{
        val out=mutableListOf<PacketRecord>();var off=0;var order=ByteOrder.LITTLE_ENDIAN;var n=1
        while(off+12<=bytes.size&&out.size<maxPackets){
            val rawType=readU32(bytes,off,ByteOrder.BIG_ENDIAN)
            if(rawType==0x0A0D0D0AL){
                if(off+28>bytes.size)break
                val bom=readU32(bytes,off+8,ByteOrder.BIG_ENDIAN);order=when(bom){0x1A2B3C4DL->ByteOrder.BIG_ENDIAN;0x4D3C2B1AL->ByteOrder.LITTLE_ENDIAN;else->return out}
            }
            val blockType=readU32(bytes,off,order);val len=readU32(bytes,off+4,order).toInt()
            if(len<12||off+len>bytes.size||len%4!=0)break
            if(blockType==6L&&len>=32){
                val interfaceId=readU32(bytes,off+8,order).toInt();val tsHigh=readU32(bytes,off+12,order);val tsLow=readU32(bytes,off+16,order);val capLen=readU32(bytes,off+20,order).toInt();val dataOffset=off+28
                if(interfaceId>=0&&capLen>=0&&dataOffset+capLen<=off+len-4){out+=decodeFrame(bytes,dataOffset,capLen,n,"$tsHigh.$tsLow");n++}
            }
            off+=len
        }
        return out
    }

    private fun decodeFrame(b:ByteArray,o:Int,len:Int,n:Int,ts:String):PacketRecord{
        if(len<14)return PacketRecord(n,ts,protocol="ETHERNET",info="truncated frame",length=len)
        val dst=mac(b,o);val src=mac(b,o+6);var ether=readU16(b,o+12,ByteOrder.BIG_ENDIAN);var vlan:Int?=null;var l3=o+14
        if((ether==0x8100||ether==0x88a8)&&len>=18){vlan=readU16(b,o+14,ByteOrder.BIG_ENDIAN) and 0x0fff;ether=readU16(b,o+16,ByteOrder.BIG_ENDIAN);l3=o+18}
        if(ether==0x0806)return decodeArp(b,l3,len-(l3-o),n,ts,src,dst,vlan)
        if(ether==0x86dd)return decodeIpv6(b,l3,len-(l3-o),n,ts,src,dst,vlan)
        if(ether!=0x0800)return PacketRecord(n,ts,src,dst,"ETHERNET","EtherType 0x${ether.toString(16)}",len,vlanId=vlan)
        return decodeIpv4(b,l3,len-(l3-o),n,ts,src,dst,vlan)
    }
    private fun decodeArp(b:ByteArray,o:Int,len:Int,n:Int,ts:String,src:String,dst:String,vlan:Int?):PacketRecord{
        if(len<28)return PacketRecord(n,ts,src,dst,"ARP","invalid ARP",len,vlanId=vlan)
        val op=readU16(b,o+6,ByteOrder.BIG_ENDIAN);val sha=mac(b,o+8);val spa=ipv4(b,o+14);val tpa=ipv4(b,o+24);val text=when(op){1->"who-has $tpa tell $spa";2->"$spa is-at $sha";else->"opcode=$op $spa → $tpa"}
        return PacketRecord(n,ts,spa,tpa,"ARP",text,len,vlanId=vlan)
    }
    private fun decodeIpv4(b:ByteArray,o:Int,len:Int,n:Int,ts:String,ethSrc:String,ethDst:String,vlan:Int?):PacketRecord{
        if(len<20)return PacketRecord(n,ts,ethSrc,ethDst,"IPv4","invalid IPv4 header",len,vlanId=vlan,ipVersion=4)
        val ihl=(b[o].toInt() and 0x0f)*4;if(ihl<20||len<ihl)return PacketRecord(n,ts,ethSrc,ethDst,"IPv4","invalid IHL",len,vlanId=vlan,ipVersion=4)
        val total=readU16(b,o+2,ByteOrder.BIG_ENDIAN);val proto=b[o+9].toInt() and 255;val src=ipv4(b,o+12);val dst=ipv4(b,o+16)
        return when(proto){
            1->PacketRecord(n,ts,src,dst,"ICMP",icmpInfo(b,o+ihl,len-ihl),len,vlanId=vlan,ipVersion=4)
            6->decodeTcp(b,o+ihl,len-ihl,n,ts,src,dst,vlan,4)
            17->decodeUdp(b,o+ihl,len-ihl,n,ts,src,dst,vlan,4)
            else->PacketRecord(n,ts,src,dst,"IPv4","proto=$proto len=$total",len,vlanId=vlan,ipVersion=4)
        }
    }
    private fun decodeIpv6(b:ByteArray,o:Int,len:Int,n:Int,ts:String,ethSrc:String,ethDst:String,vlan:Int?):PacketRecord{
        if(len<40)return PacketRecord(n,ts,ethSrc,ethDst,"IPv6","invalid IPv6 header",len,vlanId=vlan,ipVersion=6)
        val next=b[o+6].toInt() and 255;val src=ipv6(b,o+8);val dst=ipv6(b,o+24);val proto=when(next){58->"ICMPv6";6->"TCP";17->"UDP";else->"IPv6/$next"}
        return PacketRecord(n,ts,src,dst,proto,"IPv6 next-header=$next",len,vlanId=vlan,ipVersion=6)
    }
    private fun decodeTcp(b:ByteArray,o:Int,len:Int,n:Int,ts:String,src:String,dst:String,vlan:Int?,ipVer:Int):PacketRecord{
        if(len<20)return PacketRecord(n,ts,src,dst,"TCP","invalid TCP header",len,vlanId=vlan,ipVersion=ipVer)
        val sp=readU16(b,o,ByteOrder.BIG_ENDIAN);val dp=readU16(b,o+2,ByteOrder.BIG_ENDIAN);val flags=readU16(b,o+12,ByteOrder.BIG_ENDIAN) and 0x01ff;val flagNames=flags(flags)
        val stream=(sp*31+dp*17)%4096;val app=when{sp==443||dp==443->"HTTPS/TLS";sp==80||dp==80->"HTTP";sp==22||dp==22->"SSH";sp==53||dp==53->"DNS-over-TCP";sp==5060||dp==5060->"SIP";else->"TCP"}
        return PacketRecord(n,ts,src,dst,"TCP","$app $sp → $dp $flagNames",len,sp,dp,flagNames,vlan,ipVer,stream)
    }
    private fun decodeUdp(b:ByteArray,o:Int,len:Int,n:Int,ts:String,src:String,dst:String,vlan:Int?,ipVer:Int):PacketRecord{
        if(len<8)return PacketRecord(n,ts,src,dst,"UDP","invalid UDP header",len,vlanId=vlan,ipVersion=ipVer)
        val sp=readU16(b,o,ByteOrder.BIG_ENDIAN);val dp=readU16(b,o+2,ByteOrder.BIG_ENDIAN);val app=when{sp==53||dp==53->"DNS";sp==67||dp==67||sp==68||dp==68->"DHCP";sp==5060||dp==5060->"SIP";sp in 16384..32767||dp in 16384..32767->"RTP";else->"UDP"}
        return PacketRecord(n,ts,src,dst,"$app", "$app $sp → $dp",len,sp,dp,vlanId=vlan,ipVersion=ipVer)
    }
    private fun icmpInfo(b:ByteArray,o:Int,len:Int)=if(len>=8){"type=${b[o].toInt() and 255} code=${b[o+1].toInt() and 255}"}else{"truncated"}
    private fun flags(f:Int)=buildList{if(f and 0x100!=0)add("NS");if(f and 0x080!=0)add("CWR");if(f and 0x040!=0)add("ECE");if(f and 0x020!=0)add("URG");if(f and 0x010!=0)add("ACK");if(f and 0x008!=0)add("PSH");if(f and 0x004!=0)add("RST");if(f and 0x002!=0)add("SYN");if(f and 0x001!=0)add("FIN")}.joinToString(",")
    private fun readU32(b:ByteArray,o:Int,order:ByteOrder)=ByteBuffer.wrap(b,o,4).order(order).int.toLong() and 0xffffffffL
    private fun readU16(b:ByteArray,o:Int,order:ByteOrder)=ByteBuffer.wrap(b,o,2).order(order).short.toInt() and 0xffff
    private fun ipv4(b:ByteArray,o:Int)=(0..3).joinToString("."){(b[o+it].toInt() and 255).toString()}
    private fun ipv6(b:ByteArray,o:Int)=(0 until 16 step 2).joinToString(":"){((b[o+it].toInt() and 255)*256+(b[o+it+1].toInt() and 255)).toString(16)}
    private fun mac(b:ByteArray,o:Int)=(0..5).joinToString(":"){(b[o+it].toInt() and 255).toString(16).padStart(2,'0')}
}
