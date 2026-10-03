package com.example.netmaster.domain

/** Hop-by-hop packet path analysis across topology devices (offline, deterministic). */
data class PacketHop(
    val step: Int,
    val deviceId: String,
    val deviceName: String,
    val deviceType: String,
    val action: String,
    val layer: String,
    val detail: String,
    val ok: Boolean = true
)

data class PacketPathResult(
    val scenarioId: String,
    val title: String,
    val summary: String,
    val hops: List<PacketHop>,
    val protocols: List<String>,
    val brokenAt: String? = null
)

data class TopologyPreset(
    val id: String,
    val title: String,
    val description: String,
    val nodes: List<TwinNode>,
    val links: List<TwinLink>
)

object PacketPathEngine {

    fun presets(): List<TopologyPreset> = listOf(
        TopologyPreset(
            id = "services",
            title = "سرویس‌های TCP/UDP",
            description = "Client + Switch + Router + FW + HTTP/HTTPS/FTP/SSH/DNS/Mail",
            nodes = listOf(
                TwinNode("user", "Client PC", "Host", "192.168.10.50", 10),
                TwinNode("acc", "Access Switch", "Switch"),
                TwinNode("core", "Core Switch", "Switch"),
                TwinNode("edge", "Edge Router", "Router", "10.0.0.1"),
                TwinNode("fw", "Firewall", "Firewall", "10.0.0.254"),
                TwinNode("web", "Web Server", "Server", "192.168.20.80", 20),
                TwinNode("ftp", "FTP Server", "Server", "192.168.20.21", 20),
                TwinNode("ssh", "SSH Server", "Server", "192.168.20.22", 20),
                TwinNode("dns", "DNS Server", "Server", "192.168.20.10", 20),
                TwinNode("mail", "Mail Server", "Server", "192.168.20.25", 20),
                TwinNode("inet", "Internet", "Cloud", "8.8.8.8")
            ),
            links = listOf(
                TwinLink("user", "acc", "Access VLAN10"),
                TwinLink("acc", "core", "Trunk"),
                TwinLink("core", "edge", "802.1Q"),
                TwinLink("edge", "fw", "Ethernet"),
                TwinLink("fw", "inet", "WAN"),
                TwinLink("core", "web", "VLAN20 HTTP/S"),
                TwinLink("core", "ftp", "VLAN20 FTP"),
                TwinLink("core", "ssh", "VLAN20 SSH"),
                TwinLink("core", "dns", "VLAN20 DNS"),
                TwinLink("core", "mail", "VLAN20 SMTP")
            )
        ),
        TopologyPreset(
            id = "campus",
            title = "Campus Enterprise",
            description = "Edge + Core + Access + Server + DNS + PBX + Client + Firewall + Internet",
            nodes = listOf(
                TwinNode("inet", "Internet", "Cloud", "8.8.8.8"),
                TwinNode("fw", "Firewall", "Firewall", "10.0.0.254"),
                TwinNode("edge", "Edge Router", "Router", "10.0.0.1"),
                TwinNode("core", "Core Switch", "Switch"),
                TwinNode("acc", "Access Switch", "Switch"),
                TwinNode("user", "Client PC", "Host", "192.168.10.50", 10),
                TwinNode("srv", "App Server", "Server", "192.168.20.20", 20),
                TwinNode("dns", "DNS Server", "Server", "192.168.20.10", 20),
                TwinNode("pbx", "PBX", "VoIP", "192.168.30.10", 30),
                TwinNode("ap", "WiFi AP", "AP", vlan = 10)
            ),
            links = listOf(
                TwinLink("inet", "fw", "WAN"),
                TwinLink("fw", "edge", "Ethernet"),
                TwinLink("edge", "core", "802.1Q"),
                TwinLink("core", "acc", "Trunk"),
                TwinLink("acc", "user", "Access VLAN10"),
                TwinLink("acc", "ap", "Access VLAN10"),
                TwinLink("core", "srv", "VLAN20"),
                TwinLink("core", "dns", "VLAN20"),
                TwinLink("core", "pbx", "VLAN30")
            )
        ),
        TopologyPreset(
            id = "branch",
            title = "Branch Office",
            description = "Router + Switch + Clients + Local DNS",
            nodes = listOf(
                TwinNode("inet", "ISP", "Cloud"),
                TwinNode("r1", "Branch Router", "Router", "10.1.0.1"),
                TwinNode("sw1", "Branch Switch", "Switch"),
                TwinNode("pc1", "PC-1", "Host", "192.168.1.10", 1),
                TwinNode("pc2", "PC-2", "Host", "192.168.1.11", 1),
                TwinNode("dns", "Local DNS", "Server", "192.168.1.2", 1)
            ),
            links = listOf(
                TwinLink("inet", "r1", "PPPoE"),
                TwinLink("r1", "sw1", "Ethernet"),
                TwinLink("sw1", "pc1", "Access"),
                TwinLink("sw1", "pc2", "Access"),
                TwinLink("sw1", "dns", "Access")
            )
        ),
        TopologyPreset(
            id = "dc",
            title = "Data Center",
            description = "Spine-Leaf + LB + App + DB + Firewall",
            nodes = listOf(
                TwinNode("spine", "Spine", "Switch"),
                TwinNode("leaf1", "Leaf-1", "Switch"),
                TwinNode("leaf2", "Leaf-2", "Switch"),
                TwinNode("lb", "Load Balancer", "LB", "10.10.0.10"),
                TwinNode("app", "App Node", "Server", "10.10.1.10"),
                TwinNode("db", "Database", "Server", "10.10.2.10"),
                TwinNode("fw", "DC Firewall", "Firewall")
            ),
            links = listOf(
                TwinLink("fw", "spine", "L3"),
                TwinLink("spine", "leaf1", "Fabric"),
                TwinLink("spine", "leaf2", "Fabric"),
                TwinLink("leaf1", "lb", "VLAN100"),
                TwinLink("leaf1", "app", "VLAN100"),
                TwinLink("leaf2", "db", "VLAN200")
            )
        )
    )

    fun analyze(scenarioId: String, state: TwinState): PacketPathResult {
        val sim = state.simulator
        val nodes = state.nodes.associateBy { it.id }
        fun hop(step: Int, id: String, action: String, layer: String, detail: String, ok: Boolean = true): PacketHop {
            val n = nodes[id]
            return PacketHop(step, id, n?.name ?: id, n?.type ?: "Device", action, layer, detail, ok)
        }
        fun nameOr(id: String, fallback: String) = nodes[id]?.name ?: fallback

        return when (scenarioId) {
            "tcp" -> {
                val fwOk = sim?.firewallHealthy != false
                val hops = buildList {
                    add(hop(1, "user", "SYN", "L4 TCP", "Client → Server :80  flags=SYN seq=0"))
                    add(hop(2, "acc", "Forward", "L2", "MAC table → Core"))
                    add(hop(3, "core", "Switch", "L2", "به ${nameOr("web", "Web Server")}" ))
                    if (!fwOk) {
                        add(hop(4, "fw", "Drop SYN", "L4", "فایروال TCP SYN را drop کرد", false))
                    } else {
                        add(hop(4, "web", "SYN-ACK", "L4 TCP", "Server پاسخ: SYN,ACK seq=0 ack=1"))
                        add(hop(5, "user", "ACK", "L4 TCP", "Handshake سه‌مرحله‌ای کامل — Connection Established"))
                    }
                }
                PacketPathResult(
                    "tcp", "توپولوژی TCP (Three-Way Handshake)",
                    if (fwOk) "اتصال TCP برقرار شد (SYN → SYN-ACK → ACK)" else "TCP در فایروال قطع شد",
                    hops, listOf("TCP"), if (!fwOk) "fw" else null
                )
            }
            "udp" -> {
                val dnsOk = sim?.dnsHealthy != false
                val hops = buildList {
                    add(hop(1, "user", "UDP Datagram", "L4 UDP", "Client → DNS :53  (بدون handshake)"))
                    add(hop(2, "acc", "Forward", "L2", "سوییچ فوروارد"))
                    add(hop(3, "core", "L2/L3", "L3", "به DNS Server"))
                    if (!dnsOk) {
                        add(hop(4, "dns", "No reply", "L4 UDP", "UDP بدون ACK — timeout در Client", false))
                    } else {
                        add(hop(4, "dns", "UDP Reply", "L4 UDP", "پاسخ DNS در یک دیتاگرم"))
                        add(hop(5, "user", "Receive", "L4 UDP", "Client دیتاگرم را دریافت کرد"))
                    }
                }
                PacketPathResult(
                    "udp", "توپولوژی UDP (Connectionless)",
                    if (dnsOk) "UDP بدون اتصال — ارسال و پاسخ مستقیم" else "UDP بدون پاسخ (سرویس down)",
                    hops, listOf("UDP"), if (!dnsOk) "dns" else null
                )
            }
            "ftp" -> {
                val fwOk = sim?.firewallHealthy != false
                val hops = buildList {
                    add(hop(1, "user", "TCP SYN :21", "L4 TCP", "Client → FTP Server 192.168.20.21:21 (Control)"))
                    add(hop(2, "acc", "Forward", "L2", "Access → Core"))
                    add(hop(3, "core", "VLAN20", "L2", "به ${nameOr("ftp", "FTP Server")}" ))
                    if (!fwOk) {
                        add(hop(4, "fw", "Block 21", "L4", "فایروال پورت 21 را بسته است", false))
                    } else {
                        add(hop(4, "ftp", "220 Ready", "L7 FTP", "Control channel: USER / PASS"))
                        add(hop(5, "ftp", "PASV", "L7 FTP", "حالت Passive — پورت داده پویا (مثلاً 50000)"))
                        add(hop(6, "user", "DATA :20/passive", "L4 TCP", "کانال داده جدا — RETR/STOR فایل"))
                        add(hop(7, "ftp", "Transfer OK", "L7 FTP", "226 Transfer complete"))
                    }
                }
                PacketPathResult(
                    "ftp", "توپولوژی FTP (Control + Data)",
                    if (fwOk) "FTP: کانال کنترل 21 + کانال داده" else "FTP در فایروال مسدود",
                    hops, listOf("TCP", "FTP"), if (!fwOk) "fw" else null
                )
            }
            "ssh" -> {
                val fwOk = sim?.firewallHealthy != false
                val hops = buildList {
                    add(hop(1, "user", "TCP SYN :22", "L4 TCP", "Client → SSH Server 192.168.20.22:22"))
                    add(hop(2, "acc", "Forward", "L2", "به Core"))
                    add(hop(3, "core", "VLAN20", "L2", "به ${nameOr("ssh", "SSH Server")}" ))
                    if (!fwOk) {
                        add(hop(4, "fw", "Drop 22", "L4", "SSH در ACL فایروال deny شده", false))
                    } else {
                        add(hop(4, "ssh", "TCP Established", "L4 TCP", "Handshake کامل روی پورت 22"))
                        add(hop(5, "ssh", "SSH Version", "L7 SSH", "SSH-2.0 banner + Key Exchange (KEX)"))
                        add(hop(6, "user", "Auth", "L7 SSH", "رمزنگاری کانال — password/key auth"))
                        add(hop(7, "ssh", "Shell", "L7 SSH", "جلسه تعاملی امن برقرار"))
                    }
                }
                PacketPathResult(
                    "ssh", "توپولوژی SSH (TCP/22)",
                    if (fwOk) "SSH امن از Client تا Server" else "SSH مسدود در فایروال",
                    hops, listOf("TCP", "SSH"), if (!fwOk) "fw" else null
                )
            }
            "http" -> {
                val fwOk = sim?.firewallHealthy != false
                val hops = buildList {
                    add(hop(1, "user", "TCP SYN :80", "L4 TCP", "Client → Web 192.168.20.80:80"))
                    add(hop(2, "acc", "Forward", "L2", "Access Switch"))
                    add(hop(3, "core", "VLAN20", "L2", "به ${nameOr("web", "Web Server")}" ))
                    if (!fwOk) {
                        add(hop(4, "fw", "Drop HTTP", "L4", "پورت 80 بسته است", false))
                    } else {
                        add(hop(4, "web", "SYN-ACK", "L4 TCP", "اتصال TCP برقرار"))
                        add(hop(5, "user", "GET /", "L7 HTTP", "HTTP/1.1 GET / index.html Host: web.local"))
                        add(hop(6, "web", "200 OK", "L7 HTTP", "Response headers + body (plaintext)"))
                    }
                }
                PacketPathResult(
                    "http", "توپولوژی HTTP (TCP/80)",
                    if (fwOk) "درخواست HTTP و پاسخ 200" else "HTTP مسدود",
                    hops, listOf("TCP", "HTTP"), if (!fwOk) "fw" else null
                )
            }
            "https" -> {
                val natOk = sim?.natHealthy != false
                val fwOk = sim?.firewallHealthy != false
                val hops = buildList {
                    add(hop(1, "user", "TCP SYN :443", "L4 TCP", "Client → Server:443 یا Internet"))
                    add(hop(2, "acc", "Forward", "L2", "به Edge"))
                    add(hop(3, "edge", "Route/NAT", "L3", if (natOk) "مسیریابی + srcnat در صورت اینترنت" else "NAT خراب", natOk))
                    if (!natOk) {
                        add(hop(4, "edge", "NAT fail", "L3/L4", "ترجمه آدرس انجام نشد", false))
                    } else if (!fwOk) {
                        add(hop(4, "fw", "Drop 443", "L4", "HTTPS در فایروال drop شد", false))
                    } else {
                        add(hop(4, "fw", "Allow 443", "L4", "سیاست allow HTTPS"))
                        add(hop(5, "web", "TCP OK", "L4 TCP", "Handshake TCP کامل"))
                        add(hop(6, "web", "TLS Handshake", "L6 TLS", "ClientHello → ServerHello → Certificate → Finished"))
                        add(hop(7, "user", "HTTP over TLS", "L7 HTTPS", "GET رمزشده — Application Data"))
                    }
                }
                PacketPathResult(
                    "https", "توپولوژی HTTPS (TCP/443 + TLS)",
                    when {
                        !natOk -> "قطع در NAT"
                        !fwOk -> "قطع در Firewall"
                        else -> "HTTPS: TCP + TLS + HTTP امن"
                    },
                    hops, listOf("TCP", "TLS", "HTTPS"),
                    when {
                        !natOk -> "edge"
                        !fwOk -> "fw"
                        else -> null
                    }
                )
            }
            "dns" -> {
                val dnsOk = sim?.dnsHealthy != false
                val fwOk = sim?.firewallHealthy != false
                val hops = buildList {
                    add(hop(1, "user", "Query", "L7 DNS", "UDP Client → 192.168.20.10:53  A? example.com"))
                    add(hop(2, "acc", "Forward", "L2", "Switch → Core"))
                    add(hop(3, "core", "VLAN20", "L2", "به DNS Server"))
                    when {
                        !dnsOk -> add(hop(4, "dns", "Timeout", "L7 DNS", "سرویس DNS down", false))
                        !fwOk -> {
                            add(hop(4, "fw", "Drop UDP/53", "L4", "فایروال DNS را drop کرد", false))
                        }
                        else -> {
                            add(hop(4, "dns", "Answer", "L7 DNS", "A 93.184.216.34 TTL=300"))
                            add(hop(5, "user", "Cache", "L7", "Client IP را در cache می‌گذارد"))
                        }
                    }
                }
                PacketPathResult(
                    "dns", "توپولوژی DNS (UDP/53)",
                    if (dnsOk && fwOk) "Resolve موفق" else "DNS قطع",
                    hops, listOf("UDP", "DNS"), if (!dnsOk) "dns" else if (!fwOk) "fw" else null
                )
            }
            "dhcp" -> {
                val dhcpOk = sim?.dhcpHealthy != false
                val hops = buildList {
                    add(hop(1, "user", "Discover", "L7 DHCP", "Broadcast UDP 67/68"))
                    add(hop(2, "acc", "Flood", "L2", "Broadcast در VLAN"))
                    if (!dhcpOk) {
                        add(hop(3, "edge", "No Offer", "L7 DHCP", "DHCP server پاسخ نداد", false))
                    } else {
                        add(hop(3, "edge", "Offer", "L7 DHCP", "IP + Gateway + DNS"))
                        add(hop(4, "user", "Request", "L7 DHCP", "درخواست همان Lease"))
                        add(hop(5, "edge", "ACK", "L7 DHCP", "DORA کامل"))
                    }
                }
                PacketPathResult(
                    "dhcp", "توپولوژی DHCP (UDP 67/68)",
                    if (dhcpOk) "DORA کامل" else "Discover بدون Offer",
                    hops, listOf("UDP", "DHCP"), if (!dhcpOk) "edge" else null
                )
            }
            "smtp" -> {
                val fwOk = sim?.firewallHealthy != false
                val hops = buildList {
                    add(hop(1, "user", "TCP SYN :25", "L4 TCP", "Client/MTA → Mail :25"))
                    add(hop(2, "core", "VLAN20", "L2", "به ${nameOr("mail", "Mail Server")}" ))
                    if (!fwOk) {
                        add(hop(3, "fw", "Block SMTP", "L4", "پورت 25 مسدود (anti-spam)", false))
                    } else {
                        add(hop(3, "mail", "220 SMTP", "L7 SMTP", "EHLO → MAIL FROM → RCPT TO → DATA"))
                        add(hop(4, "mail", "250 OK", "L7 SMTP", "پیام در صف ارسال"))
                    }
                }
                PacketPathResult(
                    "smtp", "توپولوژی SMTP (TCP/25)",
                    if (fwOk) "ایمیل پذیرفته شد" else "SMTP مسدود",
                    hops, listOf("TCP", "SMTP"), if (!fwOk) "fw" else null
                )
            }
            "voip" -> {
                val qosOk = sim?.qosHealthy != false
                val hops = buildList {
                    add(hop(1, "user", "SIP INVITE", "L7 SIP", "UDP/TCP → PBX :5060"))
                    add(hop(2, "core", "VLAN Voice", "L2", "به PBX"))
                    add(hop(3, "pbx", "200 OK", "L7 SIP", "تماس — سپس RTP"))
                    if (!qosOk) {
                        add(hop(4, "core", "Loss", "QoS", "صف Voice congested", false))
                    } else {
                        add(hop(4, "core", "Priority", "QoS", "RTP با اولویت"))
                        add(hop(5, "user", "RTP", "L4 UDP", "رسانه صوتی"))
                    }
                }
                PacketPathResult(
                    "voip", "توپولوژی VoIP (SIP/RTP)",
                    if (qosOk) "Voice سالم" else "QoS مشکل دارد",
                    hops, listOf("SIP", "RTP", "UDP"), if (!qosOk) "core" else null
                )
            }
            else -> PacketPathResult(scenarioId, "سناریو نامشخص", "شناسه پشتیبانی نشده", emptyList(), emptyList())
        }
    }

    fun scenarios(): List<Pair<String, String>> = listOf(
        "tcp" to "TCP Handshake",
        "udp" to "UDP Datagram",
        "http" to "HTTP :80",
        "https" to "HTTPS :443",
        "ftp" to "FTP :21",
        "ssh" to "SSH :22",
        "dns" to "DNS :53",
        "dhcp" to "DHCP",
        "smtp" to "SMTP :25",
        "voip" to "VoIP SIP/RTP"
    )
}
