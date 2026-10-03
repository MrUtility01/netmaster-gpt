package com.example.netmaster.domain

import java.util.Locale

/** Pure deterministic network CLI sandbox. It never opens a socket or touches a real device. */
data class SimulatedCommandResult(
    val command: String,
    val output: String,
    val state: SimNetworkState,
    val changed: Boolean = false,
    val success: Boolean = true,
    val category: String = "show"
)

data class SimInterface(val name: String, val up: Boolean = true, val vlan: Int? = null, val mtu: Int = 1500, val addresses: List<String> = emptyList())
data class SimRoute(val prefix: String, val nextHop: String?, val protocol: String = "static", val distance: Int = 1, val metric: Int = 0)

data class SimNetworkState(
    val nodes: List<TwinNode>,
    val links: List<TwinLink>,
    val vlans: Set<Int> = setOf(10, 20, 30, 40, 50, 60),
    val routes: List<SimRoute> = listOf(
        SimRoute("0.0.0.0/0", "10.0.0.254", "static"),
        SimRoute("192.168.10.0/24", null, "connected"),
        SimRoute("192.168.20.0/24", null, "connected"),
        SimRoute("192.168.30.0/24", null, "connected")
    ),
    val arp: Map<String, String> = mapOf(
        "192.168.10.1" to "02:10:00:00:00:01",
        "192.168.20.10" to "02:20:00:00:00:10",
        "192.168.20.80" to "02:20:00:00:00:80",
        "192.168.20.21" to "02:20:00:00:00:21",
        "192.168.20.22" to "02:20:00:00:00:22"
    ),
    val macTable: Map<String, String> = mapOf(
        "02:10:00:00:00:01" to "Gi1/0/10",
        "02:20:00:00:00:10" to "Gi1/0/20",
        "02:20:00:00:00:80" to "Gi1/0/21"
    ),
    val interfaces: List<SimInterface> = listOf(
        SimInterface("Gi1/0/1", true, null, 1500, listOf("10.0.0.2/30")),
        SimInterface("Gi1/0/10", true, 10, 1500, listOf("192.168.10.1/24")),
        SimInterface("Gi1/0/20", true, 20, 1500, listOf("192.168.20.1/24")),
        SimInterface("Gi1/0/30", true, 30, 1500, listOf("192.168.30.1/24"))
    ),
    val dnsHealthy: Boolean = true,
    val dhcpHealthy: Boolean = true,
    val natHealthy: Boolean = true,
    val firewallHealthy: Boolean = true,
    val ospfHealthy: Boolean = true,
    val bgpHealthy: Boolean = true,
    val stpHealthy: Boolean = true,
    val qosHealthy: Boolean = true
)

class CommandSimulator {
    fun defaultState(): SimNetworkState {
        // Default = services topology (TCP/UDP apps: HTTP HTTPS FTP SSH DNS Mail)
        val nodes = listOf(
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
        )
        val links = listOf(
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
        return SimNetworkState(nodes, links)
    }

    fun execute(state: SimNetworkState, raw: String): SimulatedCommandResult {
        val cmd = raw.trim()
        if (cmd.isBlank()) return result(cmd, "No command", state, false, false, "error")
        val l = cmd.lowercase(Locale.ROOT)
        return when {
            l == "show version" || l == "/system resource print" ->
                result(cmd, "NetMaster Network Simulator\nTopology: TCP/UDP services (HTTP FTP SSH DNS Mail)", state)
            l == "show vlan" || l == "show vlan brief" ->
                result(cmd, state.vlans.sorted().joinToString("\n") { "$it\tACTIVE" }, state)
            l == "show interfaces trunk" ->
                result(cmd, state.links.filter { it.protocol.contains("802.1Q") || it.protocol == "Trunk" }.joinToString("\n") {
                    "${it.from}<->${it.to}\tallowed=${state.vlans.sorted().joinToString(",")}"
                }, state)
            l == "show interfaces status" || l == "/interface print" ->
                result(cmd, state.interfaces.joinToString("\n") {
                    "${it.name}\t${if (it.up) "up" else "down"}\tVLAN=${it.vlan ?: "-"}\tMTU=${it.mtu}\t${it.addresses.joinToString(",")}"
                }, state)
            l == "show mac address-table" ->
                result(cmd, state.macTable.entries.joinToString("\n") { "${it.key}\t${it.value}" }, state)
            l == "show spanning-tree" ->
                result(cmd, if (state.stpHealthy) "Root bridge: CORE\nRSTP: forwarding" else "Topology change detected", state)
            l == "show ip route" || l == "ip route" ->
                result(cmd, state.routes.joinToString("\n") { routeLine(it) }, state)
            l == "show arp" || l == "/ip arp print" || l == "arp -a" ->
                result(cmd, state.arp.entries.joinToString("\n") { "${it.key}\t${it.value}" }, state)
            l == "show ip ospf neighbor" ->
                result(cmd, if (state.ospfHealthy) "Neighbor 10.0.0.2\tFULL" else "Neighbor 10.0.0.2\tEXSTART", state)
            l == "show ip bgp summary" ->
                result(cmd, if (state.bgpHealthy) "10.0.0.9\tEstablished\tPrefixes=42" else "10.0.0.9\tActive\tPrefixes=0", state)
            l == "show firewall" || l == "/ip firewall filter print" ->
                result(cmd, if (state.firewallHealthy) "allow: 22,80,443,21,53,25 established\nforward=stateful" else "rule 0: DROP all", state)
            l == "show nat" || l == "/ip firewall nat print" ->
                result(cmd, if (state.natHealthy) "srcnat masquerade: active" else "srcnat rule inactive", state)
            l == "show dhcp" || l == "/ip dhcp-server print" ->
                result(cmd, if (state.dhcpHealthy) "DHCP server: running\nScope: 192.168.10.100-200" else "DHCP server: stopped", state)
            l == "show dns" || l == "/ip dns print" ->
                result(cmd, if (state.dnsHealthy) "DNS: reachable\nServer: 192.168.20.10" else "DNS: unreachable", state)
            l == "show qos" ->
                result(cmd, if (state.qosHealthy) "VoIP class: priority 1" else "Voice queue: congestion", state)
            l == "show topology" || l == "show nodes" ->
                result(
                    cmd,
                    state.nodes.joinToString("\n") { "${it.id}\t${it.name}\t${it.type}\t${it.ip ?: "-"}\tvlan=${it.vlan ?: "-"}" } +
                        "\n---\n" + state.links.joinToString("\n") { "${it.from} -> ${it.to}\t${it.protocol}\t${if (it.up) "UP" else "DOWN"}" },
                    state
                )
            l == "show services" ->
                result(
                    cmd,
                    "HTTP   TCP/80   web  192.168.20.80\nHTTPS  TCP/443  web  192.168.20.80\nFTP    TCP/21   ftp  192.168.20.21\nSSH    TCP/22   ssh  192.168.20.22\nDNS    UDP/53   dns  192.168.20.10\nSMTP   TCP/25   mail 192.168.20.25\nDHCP   UDP/67   edge",
                    state
                )
            l.startsWith("ping ") -> ping(state, cmd.substringAfter(' ').trim())
            l.startsWith("traceroute ") || l.startsWith("tracert ") -> trace(state, cmd.substringAfter(' ').trim())
            l.startsWith("nslookup ") || l.startsWith("dig ") -> dns(state, cmd.substringAfter(' ').trim())
            l.startsWith("tcpdump ") || l.startsWith("torch ") ->
                result(
                    cmd,
                    "SIM-CAPTURE\n" +
                        "1 TCP 192.168.10.50:51522 → 192.168.20.80:443 SYN\n" +
                        "2 TCP 192.168.20.80:443 → 192.168.10.50:51522 SYN,ACK\n" +
                        "3 TCP 192.168.10.50:51523 → 192.168.20.22:22 SSH\n" +
                        "4 TCP 192.168.10.50:51524 → 192.168.20.21:21 FTP\n" +
                        "5 UDP 192.168.10.50:5353 → 192.168.20.10:53 DNS",
                    state
                )
            l == "disable dns" -> mutate(cmd, state.copy(dnsHealthy = false), "DNS disabled")
            l == "enable dns" -> mutate(cmd, state.copy(dnsHealthy = true), "DNS enabled")
            l == "disable firewall" -> mutate(cmd, state.copy(firewallHealthy = false), "Firewall degraded")
            l == "enable firewall" -> mutate(cmd, state.copy(firewallHealthy = true), "Firewall healthy")
            l == "disable nat" -> mutate(cmd, state.copy(natHealthy = false), "NAT disabled")
            l == "enable nat" -> mutate(cmd, state.copy(natHealthy = true), "NAT enabled")
            l == "disable dhcp" -> mutate(cmd, state.copy(dhcpHealthy = false), "DHCP stopped")
            l == "enable dhcp" -> mutate(cmd, state.copy(dhcpHealthy = true), "DHCP running")
            l == "disable qos" -> mutate(cmd, state.copy(qosHealthy = false), "QoS congestion")
            l == "enable qos" -> mutate(cmd, state.copy(qosHealthy = true), "QoS restored")
            l == "disable ospf" -> mutate(cmd, state.copy(ospfHealthy = false), "OSPF degraded")
            l == "enable ospf" -> mutate(cmd, state.copy(ospfHealthy = true), "OSPF OK")
            l == "disable bgp" -> mutate(cmd, state.copy(bgpHealthy = false), "BGP down")
            l == "enable bgp" -> mutate(cmd, state.copy(bgpHealthy = true), "BGP up")
            else -> result(
                cmd,
                "Simulator: show topology | show services | show vlan | ping | nslookup | tcpdump | disable/enable dns|firewall|nat|dhcp",
                state, false, false, "error"
            )
        }
    }

    private fun ping(state: SimNetworkState, target: String): SimulatedCommandResult {
        val normalized = target.trim()
        if (!state.firewallHealthy && normalized != "10.0.0.1")
            return result("ping $normalized", "Request timed out.\nSIM cause: firewall", state)
        if (normalized in listOf("192.168.20.10", "192.168.20.80", "192.168.20.21", "192.168.20.22", "192.168.20.25"))
            return result("ping $normalized", if (state.dnsHealthy || normalized != "192.168.20.10") "Reply from $normalized: time<1ms" else "Request timed out", state)
        if (normalized == "192.168.10.1" || normalized == "10.0.0.1")
            return result("ping $normalized", "Reply from $normalized: time<1ms", state)
        return if (state.routes.any { it.prefix == "0.0.0.0/0" })
            result("ping $normalized", "Reply from $normalized: time=8ms TTL=52", state)
        else result("ping $normalized", "Network is unreachable", state)
    }

    private fun trace(state: SimNetworkState, target: String): SimulatedCommandResult {
        val hops = if (state.routes.any { it.prefix == "0.0.0.0/0" })
            listOf("1  10.0.0.1 (edge)", "2  10.0.0.254 (fw)", "3  $target")
        else listOf("1  10.0.0.1", "2  * * *")
        return result("traceroute $target", hops.joinToString("\n"), state)
    }

    private fun dns(state: SimNetworkState, target: String) =
        result("nslookup $target", if (state.dnsHealthy) "Server: 192.168.20.10\nName: $target\nAddress: 93.184.216.34" else "DNS request timed out", state)

    private fun routeLine(r: SimRoute) =
        "${when (r.protocol) { "connected" -> "C"; "ospf" -> "O"; "bgp" -> "B"; else -> "S" }} ${r.prefix}${r.nextHop?.let { " via $it" }.orEmpty()}"

    private fun result(cmd: String, out: String, state: SimNetworkState, changed: Boolean = false, success: Boolean = true, category: String = "show") =
        SimulatedCommandResult(cmd, out, state, changed, success, category)

    private fun mutate(cmd: String, state: SimNetworkState, out: String, category: String = "config") =
        result(cmd, out, state, true, true, category)
}
