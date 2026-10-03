package com.example.netmaster.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.example.netmaster.ai.*
import com.example.netmaster.data.*
import com.example.netmaster.domain.*

@Composable
fun PacketPathPanel(path: PacketPathResult) {
    SectionCard(path.title, path.summary + if (path.brokenAt != null) " — قطع در: ${path.brokenAt}" else "") {
        Text("پروتکل‌ها: " + path.protocols.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        path.hops.forEach { h ->
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = if (h.ok) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${h.step}. ${h.deviceName} (${h.deviceType}) — ${h.action}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("${h.layer} · ${h.detail}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun DeviceListCard(nodes: List<TwinNode>, links: List<TwinLink>) {
    SectionCard("دیوایس‌های توپولوژی", "${nodes.size} نود · ${links.size} لینک") {
        nodes.forEach { n ->
            Text("• ${n.name}  [${n.type}]  ${n.ip ?: "—"}  ${n.vlan?.let { "VLAN $it" } ?: ""}", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(6.dp))
        Text("لینک‌ها:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        links.forEach { l ->
            Text("  ${l.from} → ${l.to}  (${l.protocol}) ${if (l.up) "UP" else "DOWN"}", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun OpsGuidePanel() {
    val topics = remember { NetworkOpsGuide.all() }
    var selected by remember { mutableStateOf(topics.first().id) }
    val t = topics.first { it.id == selected }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("راهنمای عملی: VLAN · تانل · LB · Failover · حجم", fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            topics.forEach { topic ->
                FilterChip(selected = selected == topic.id, onClick = { selected = topic.id }, label = { Text(topic.title.take(18), fontSize = 11.sp) })
            }
        }
        SectionCard(t.title, t.summary) {
            Text("مراحل اجرا:", fontWeight = FontWeight.Bold)
            t.steps.forEachIndexed { i, s -> Text("${i + 1}. $s", style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(8.dp))
            Text("دستورهای Cisco", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            BodyText(t.cisco, mono = true)
            Spacer(Modifier.height(8.dp))
            Text("دستورهای MikroTik", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
            BodyText(t.mikrotik, mono = true)
        }
    }
}

@Composable
fun LabScreen(vm: NetMasterViewModel) {
    val twin = vm.twin.collectAsState().value
    val faults = remember { vm.twinEngine.faults() }
    val presets = remember { PacketPathEngine.presets() }
    val scenarios = remember { PacketPathEngine.scenarios() }
    var presetId by remember { mutableStateOf("services") }
    var scenarioId by remember { mutableStateOf("tcp") }
    val path = remember(twin, scenarioId) { PacketPathEngine.analyze(scenarioId, twin) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard("آزمایشگاه عملی", "توپولوژی زنده · مسیر بسته · کانفیگ Cisco/MikroTik · تزریق خطا") {
                Text("۱) توپولوژی را انتخاب کن  ۲) سناریوی بسته را ببین  ۳) خطا بزن و hop قرمز را پیدا کن  ۴) راهنمای VLAN/تانل را اجرا کن.")
            }
        }
        item {
            Text("نوع توپولوژی", fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                presets.forEach { p ->
                    FilterChip(selected = presetId == p.id, onClick = { presetId = p.id; vm.loadTopologyPreset(p.id) }, label = { Text(p.title, fontSize = 11.sp) })
                }
            }
        }
        item { TopologyGraph(twin.nodes, twin.links) }
        item { DeviceListCard(twin.nodes, twin.links) }
        item {
            Text("تحلیل مسیر بسته (با تزریق خطا عوض می‌شود)", fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                scenarios.forEach { (id, title) ->
                    FilterChip(selected = scenarioId == id, onClick = { scenarioId = id }, label = { Text(title, fontSize = 11.sp) })
                }
            }
        }
        item { PacketPathPanel(path) }
        item { OpsGuidePanel() }
        item {
            SectionCard("اثبات رفع خطا", "بعد از تزریق خطا، شواهد جمع کن و نتیجه را Verify کن.") {
                val verification = vm.twinVerification.collectAsState().value
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { vm.recordEvidence("دستور/لاگ ثبت‌شده در آزمایشگاه") }, modifier = Modifier.weight(1f)) { Text("ثبت Evidence") }
                    OutlinedButton(onClick = { vm.verifyTwin() }, modifier = Modifier.weight(1f)) { Text("Verify") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { vm.repairTwin() }, modifier = Modifier.weight(1f)) { Text("Repair / Fix") }
                    OutlinedButton(onClick = { vm.completeTwin() }, modifier = Modifier.weight(1f)) { Text("تکمیل سناریو") }
                }
                verification?.let { v ->
                    Text(if (v.passed) "قبول: ${v.checks.firstOrNull() ?: "Recovery verified"}" else "نیازمند بررسی بیشتر", fontWeight = FontWeight.Bold, color = if (v.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    v.checks.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    Text("امتیاز: ${v.score}", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        item {
            SimulatorConsole(vm)
        }
        item {
            ProtocolFlowChart(title = "چرخه آزمایش قابل‌اثبات", steps = listOf("Baseline سالم", "یک خطای کنترل‌شده", "مشاهده state/packet/log", "فرضیه و تست تفکیک‌کننده", "Fix + Verify + Rollback"))
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ vm.resetTwin(); presetId = "services" }, Modifier.weight(1f)) { Text("بازنشانی") }
                OutlinedButton({ vm.saveSimulatorSnapshot("دستی") }, Modifier.weight(1f)) { Text("عکس‌فوری") }
            }
        }
        item {
            SectionCard("وضعیت سرویس‌ها") {
                twin.simulator?.let { s ->
                    Text("DNS: ${if (s.dnsHealthy) "سالم" else "خراب"}  |  DHCP: ${if (s.dhcpHealthy) "سالم" else "خراب"}")
                    Text("فایروال: ${if (s.firewallHealthy) "سالم" else "خراب"}  |  NAT: ${if (s.natHealthy) "سالم" else "خراب"}")
                    Text("OSPF: ${if (s.ospfHealthy) "بالا" else "ضعیف"}  |  BGP: ${if (s.bgpHealthy) "بالا" else "قطع"}")
                    Text("QoS: ${if (s.qosHealthy) "خوب" else "congested"}  |  STP: ${if (s.stpHealthy) "پایدار" else "ناپایدار"}")
                } ?: Text("شبیه‌ساز آماده نیست")
            }
        }
        item { Text("تزریق خطا — بعد از تزریق دوباره مسیر بسته را ببین", fontWeight = FontWeight.Bold) }
        items(faults, key = { it.id }) { f ->
            Card(onClick = { vm.injectFault(f) }, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(f.title, fontWeight = FontWeight.Bold)
                    BodyText(f.description)
                    Text("شدت: ${f.severity} · هدف: ${f.target} · راهنما: ${f.hint}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun SimulatorConsole(vm: NetMasterViewModel) {
    var command by remember { mutableStateOf("show services") }
    val output = vm.simOutput.collectAsState().value
    SectionCard("ترمینال شبیه‌ساز", "این خروجی ساختگیِ کنترل‌شده است؛ evidence تجهیز واقعی محسوب نمی‌شود.", Icons.Default.Terminal) {
        Text("فرمان‌های قابل‌آزمایش: show vlan · show interfaces trunk · show ip route · ping 192.168.20.80 · nslookup example.com · tcpdump -ni eth0 tcp · disable dns", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = command,
            onValueChange = { command = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("فرمان") },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.runSimulator(command) }, modifier = Modifier.weight(1f), enabled = command.isNotBlank()) { Text("اجرا") }
            OutlinedButton(onClick = { command = "show topology" }, modifier = Modifier.weight(1f)) { Text("Topology") }
        }
        if (output.isNotBlank()) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Text(output, modifier = Modifier.padding(12.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr, lineHeight = 18.sp))
            }
        }
    }
}

fun QuizScreen(vm: NetMasterViewModel, open: (Lesson) -> Unit) {
    val c = vm.curriculum.collectAsState().value ?: return
    val lessons = remember(c) { c.levels.flatMap { it.lessons } }
    var i by remember { mutableIntStateOf(0) }
    if (lessons.isEmpty()) {
        Text("درسی نیست", Modifier.padding(16.dp))
        return
    }
    val raw = lessons[i % lessons.size]
    val e = remember(raw.id) { LessonEnricher.enrich(raw) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            SectionCard("آزمون مفهومی", "سؤال‌های تکراری قالب حذف شده‌اند؛ روی مفهوم واقعی تمرکز است") {
                Text(e.title, fontWeight = FontWeight.Bold)
                if (e.wasEnriched) Text("محتوای این آزمون پالایش‌شده است.", style = MaterialTheme.typography.labelMedium)
            }
        }
        item { InteractiveQuizCard(e.quiz) }
        if (e.keyPoints.isNotEmpty()) {
            item {
                SectionCard("قبل از جواب — نکات") {
                    e.keyPoints.forEach { Text("• $it") }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ i++ }, Modifier.weight(1f)) { Text("درس/آزمون بعدی") }
                OutlinedButton({ open(raw) }, Modifier.weight(1f)) { Text("متن کامل درس") }
            }
        }
    }
}

@Composable
fun NotesScreen(vm: NetMasterViewModel) {
    val n = vm.notes.collectAsState().value
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { SectionCard("پایگاه دانش شخصی", "یادداشت‌ها") { Text(if (n.isEmpty()) "هنوز یادداشتی نیست." else "${n.size} یادداشت") } }
        items(n, key = { it.id }) { x ->
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(x.title, fontWeight = FontWeight.Bold)
                    BodyText(x.body)
                }
            }
        }
    }
}

@Composable
fun SearchScreen(vm: NetMasterViewModel, onLesson: (Lesson) -> Unit) {
    var q by remember { mutableStateOf("") }
    val hits = vm.searchResults.collectAsState().value
    LaunchedEffect(q) { if (q.length >= 2) vm.searchAll(q) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("جستجوی سراسری", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), label = { Text("جستجو…") }, singleLine = true)
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(hits, key = { it.id + it.kind }) { h ->
                Card(shape = RoundedCornerShape(10.dp)) {
                    ListItem(
                        headlineContent = { Text(h.title) },
                        supportingContent = { Text("${h.kind} • ${h.snippet}", maxLines = 2) },
                        trailingContent = {
                            if (h.kind == "LESSON") {
                                TextButton({
                                    vm.curriculum.value?.levels?.flatMap { it.lessons }?.firstOrNull { it.id == h.id }?.let(onLesson)
                                }) { Text("باز کردن") }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AiScreen(vm: NetMasterViewModel) {
    var prompt by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(AiMode.TROUBLESHOOTER) }
    val r = vm.aiResponse.collectAsState().value
    val backend = vm.aiBackend.collectAsState().value
    val busy = vm.aiBusy.collectAsState().value
    fun modeFa(m: AiMode) = when (m) {
        AiMode.TEACHER -> "معلم"; AiMode.SOCRATIC -> "سقراطی"; AiMode.TROUBLESHOOTER -> "عیب‌یابی"
        AiMode.EXAMINER -> "آزمون‌گر"; AiMode.LAB_COACH -> "مربی Lab"; AiMode.CONFIG_REVIEWER -> "بازبین Config"; AiMode.AUTONOMOUS_COACH -> "مربی خودکار"
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionCard("مرکز هوش مصنوعی", "پشتیبانی: $backend") { Text("علائم را به فارسی بنویس؛ پاسخ با فرضیه و اطمینان می‌آید.") } }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AiMode.entries.forEach {
                    FilterChip(selected = mode == it, onClick = { mode = it }, label = { Text(modeFa(it), fontSize = 11.sp) })
                }
            }
        }
        item {
            OutlinedTextField(prompt, { prompt = it }, Modifier.fillMaxWidth(), minLines = 3, label = { Text("سؤال یا علائم") }, enabled = !busy)
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("در حال تحلیل… برای جلوگیری از هنگ، حداکثر ۲۰ ثانیه زمان داده می‌شود.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Button({ vm.askAi(mode, prompt) }, enabled = prompt.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "در حال تحلیل…" else "تحلیل") }
        }
        r?.let {
            item {
                SectionCard(it.title) {
                    BodyText(it.answer)
                    it.hypotheses.forEach { h -> Text("فرضیه: ${h.title} (${h.confidence}٪)") }
                }
            }
        }
    }
}

@Composable
fun OpsScreen(vm: NetMasterViewModel) {
    val incidents by vm.incidents.collectAsState()
    val due by vm.dueReviews.collectAsState()
    var title by remember { mutableStateOf("") }
    var symptom by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionCard("مرکز عملیات", "رخداد + راهنمای شبکه") { Text("مرور سررسید: ${due.size}") } }
        item { OpsGuidePanel() }
        item {
            SectionCard("ثبت رخداد") {
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("عنوان") })
                OutlinedTextField(symptom, { symptom = it }, Modifier.fillMaxWidth(), label = { Text("علائم") })
                Button({
                    if (title.isNotBlank()) { vm.createIncident(title, symptom); title = ""; symptom = "" }
                }, Modifier.fillMaxWidth()) { Text("ثبت") }
            }
        }
        items(incidents.take(30), key = { it.id }) { i ->
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(i.title, fontWeight = FontWeight.Bold)
                    Text("وضعیت: ${i.status}  |  شدت: ${i.severity}")
                }
            }
        }
    }
}

@Composable
fun SimScreen(vm: NetMasterViewModel) {
    val twin = vm.twin.collectAsState().value
    val output = vm.simOutput.collectAsState().value
    var cmd by remember { mutableStateOf("") }
    var scenarioId by remember { mutableStateOf("https") }
    val path = remember(twin, scenarioId) { PacketPathEngine.analyze(scenarioId, twin) }
    val scenarios = remember { PacketPathEngine.scenarios() }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionCard("شبیه‌ساز شبکه", "دستور + مسیر بسته") { Text("show topology | show services | tcpdump | disable dns") } }
        item { TopologyGraph(twin.nodes, twin.links) }
        item { DeviceListCard(twin.nodes, twin.links) }
        item {
            Text("سناریوی بسته", fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                scenarios.forEach { (id, title) ->
                    FilterChip(selected = scenarioId == id, onClick = { scenarioId = id }, label = { Text(title, fontSize = 11.sp) })
                }
            }
        }
        item { PacketPathPanel(path) }
        item {
            OutlinedTextField(cmd, { cmd = it }, Modifier.fillMaxWidth(), label = { Text("دستور") })
            Button({ vm.runSimulator(cmd) }, Modifier.fillMaxWidth()) { Text("اجرا") }
        }
        item { SectionCard("خروجی") { BodyText(output.ifBlank { "—" }, mono = true) } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ vm.resetTwin() }, Modifier.weight(1f)) { Text("بازنشانی") }
                OutlinedButton({ vm.saveSimulatorSnapshot("شبیه‌ساز") }, Modifier.weight(1f)) { Text("عکس‌فوری") }
            }
        }
    }
}

@Composable
fun EngineeringScreen(vm: NetMasterViewModel) {
    val inv = vm.engineeringInvestigation.collectAsState().value
    val passport = vm.engineeringPassport.collectAsState().value
    val faults = vm.engineeringFaults.collectAsState().value
    val pcap = vm.pcapLoad.collectAsState().value
    val pcapReport = vm.packetAnalysis.collectAsState().value
    val diff = vm.configDiff.collectAsState().value
    val context = LocalContext.current
    var symptom by remember { mutableStateOf("") }
    var configOld by remember { mutableStateOf("interface Gi1/0/1\n ip address 10.0.0.1 255.255.255.0") }
    var configNew by remember { mutableStateOf("interface Gi1/0/1\n ip address 10.0.0.1 255.255.255.0\n shutdown") }
    var packetText by remember { mutableStateOf("1 10.0.0.1:50000 -> 10.0.0.2:443 TCP SYN\n2 10.0.0.2:443 -> 10.0.0.1:50000 TCP SYN ACK\n3 10.0.0.1:50000 -> 10.0.0.2:443 TCP ACK") }
    var pcapElapsed by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        if (bytes != null) vm.importPcap(uri.lastPathSegment ?: "capture.pcap", bytes)
    }
    LaunchedEffect(Unit) { vm.computeEngineeringPassport() }
    LaunchedEffect(pcap.busy) {
        pcapElapsed = 0
        while (pcap.busy) {
            kotlinx.coroutines.delay(1000)
            pcapElapsed += 1
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionCard("سیستم مهندس شبکه", "شواهد + پاسپورت") { Text("علائم را واضح بنویس؛ از symptom به evidence و اولین divergence برو.") } }
        item { ProtocolFlowChart(title = "زنجیره شواهد", steps = listOf("علائم", "فرضیه", "شواهد", "اولین انحراف", "رفع", "Verification")) }
        item {
            OutlinedTextField(symptom, { symptom = it }, Modifier.fillMaxWidth(), label = { Text("علائم") })
            Button({ vm.investigateEngineering(symptom, "") }, Modifier.fillMaxWidth(), enabled = symptom.isNotBlank()) { Text("شروع بررسی") }
        }
        inv?.let {
            item {
                SectionCard("اولین نقطه انحراف") {
                    BodyText(it.firstDivergence)
                    it.hypotheses.take(5).forEach { h -> Text("${h.title} — ${h.confidence}٪") }
                }
            }
        }
        item {
            SectionCard("پاسپورت مهندسی") {
                Text(if (passport != null) "امتیاز: ${passport.overall}٪" else "در حال محاسبه…", fontWeight = FontWeight.Bold)
                passport?.competencies?.forEach { Text("${it.name}: ${it.score}٪") }
            }
        }
        item {
            SectionCard("Config Diff و ریسک تغییر", "قبل/بعد را مقایسه کن؛ تغییرات پرریسک مشخص می‌شوند.") {
                OutlinedTextField(configOld, { configOld = it }, Modifier.fillMaxWidth(), minLines = 5, label = { Text("Baseline config") })
                OutlinedTextField(configNew, { configNew = it }, Modifier.fillMaxWidth(), minLines = 5, label = { Text("New config") })
                Button({ vm.compareConfigs(configOld, configNew) }, Modifier.fillMaxWidth()) { Text("تحلیل Diff") }
                diff?.let { d ->
                    Text("+${d.added} / -${d.removed} · blocks=${d.changedBlocks} · risk=${d.riskScore}", fontWeight = FontWeight.Bold)
                    d.semanticChanges.forEach { Text("• $it") }
                    d.riskFlags.forEach { Text("⚠ $it", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        item {
            SectionCard("Packet Analysis", "تجزیه و تحلیل متن capture و PCAP واقعی") {
                OutlinedTextField(packetText, { packetText = it }, Modifier.fillMaxWidth(), minLines = 7, label = { Text("Packet text / tshark-like input") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button({ vm.analyzePacketText(packetText) }, Modifier.weight(1f)) { Text("تحلیل متن") }
                    OutlinedButton({ launcher.launch(arrayOf("*/*")) }, Modifier.weight(1f)) { Text("بازکردن PCAP") }
                }
                if (pcap.busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("در حال تحلیل PCAP… ${pcapElapsed}s", style = MaterialTheme.typography.labelSmall)
                }
                pcap.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                pcapReport?.let { r ->
                    Text("Packets=${r.total} · endpoints=${r.uniqueEndpoints} · TCP handshake=${r.handshakeComplete} · failures=${r.handshakeFailures}", fontWeight = FontWeight.Bold)
                    Text("Protocols: ${r.protocolCounts.entries.sortedByDescending { it.value }.take(8).joinToString { "${it.key}:${it.value}" }}", style = MaterialTheme.typography.bodySmall)
                    r.findings.take(8).forEach { Text("• $it") }
                }
            }
        }
        item { Text("تزریق خطا", fontWeight = FontWeight.Bold) }
        items(faults, key = { it.id }) { f ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(f.title, modifier = Modifier.weight(1f))
                Button({ vm.injectEngineeringFault(f.id) }) { Text("تزریق") }
            }
        }
    }
}
