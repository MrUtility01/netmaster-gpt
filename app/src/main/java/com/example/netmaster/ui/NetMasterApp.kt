package com.example.netmaster.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.netmaster.data.*
import com.example.netmaster.domain.*
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private fun masteryFa(v: String) = when (v) {
    Mastery.UNKNOWN.name -> "بلد نیستم"
    Mastery.REVIEW.name -> "نیاز به مرور"
    Mastery.KNOWN.name -> "بلدم"
    Mastery.MASTERED.name -> "مسلط"
    else -> v
}

@Composable
fun SectionCard(
    title: String,
    subtitle: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (icon != null) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(36.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    if (!subtitle.isNullOrBlank()) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            content()
        }
    }
}

@Composable
fun BodyText(text: String, mono: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(
            textDirection = if (mono) TextDirection.Ltr else TextDirection.ContentOrRtl,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            lineHeight = 24.sp
        )
    )
}

@Composable
fun TopologyGraph(nodes: List<TwinNode>, links: List<TwinLink>, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val surfaceVar = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Hub, null, tint = primary, modifier = Modifier.size(22.dp))
                Column {
                    Text("توپولوژی شبکه", fontWeight = FontWeight.Bold, color = primary, style = MaterialTheme.typography.titleMedium)
                    Text("${nodes.size} دیوایس · ${links.size} لینک", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(10.dp))
            if (nodes.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(160.dp).background(surfaceVar, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Text("توپولوژی خالی — از آزمایشگاه بارگذاری کنید", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
                }
            } else {
                Canvas(Modifier.fillMaxWidth().height(200.dp).background(surfaceVar, RoundedCornerShape(14.dp))) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val r = min(size.width, size.height) * 0.32f
                    val n = nodes.size.coerceAtLeast(1)
                    val pos = nodes.mapIndexed { idx, node ->
                        val a = idx * 2.0 * Math.PI / n - Math.PI / 2
                        node.id to Offset(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
                    }.toMap()
                    links.forEach { link ->
                        val a = pos[link.from]; val b = pos[link.to]
                        if (a != null && b != null) {
                            drawLine(
                                color = if (link.up) outline else outline.copy(alpha = 0.4f),
                                start = a, end = b, strokeWidth = 3f,
                                pathEffect = if (link.up) null else PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                            )
                        }
                    }
                    nodes.forEach { node ->
                        val p = pos[node.id] ?: return@forEach
                        drawCircle(primary, 24f, p)
                        drawCircle(Color.White, 24f, p, style = Stroke(width = 2.5f))
                    }
                }
                Text(nodes.joinToString("  •  ") { it.name.take(14) }, style = MaterialTheme.typography.labelMedium, color = onSurface, modifier = Modifier.padding(top = 8.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun ProtocolFlowChart(title: String, steps: List<String>) {
    val primary = MaterialTheme.colorScheme.primary
    val container = MaterialTheme.colorScheme.primaryContainer
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = primary, style = MaterialTheme.typography.titleMedium)
            steps.forEachIndexed { index, step ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = CircleShape, color = primary, modifier = Modifier.size(28.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("${index + 1}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                    Surface(shape = RoundedCornerShape(12.dp), color = container, modifier = Modifier.weight(1f)) {
                        Text(step, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), fontWeight = FontWeight.Medium)
                    }
                }
                if (index < steps.lastIndex) {
                    Box(Modifier.padding(start = 10.dp).size(28.dp), contentAlignment = Alignment.Center) {
                        Text("↓", color = primary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun InteractiveQuizCard(questions: List<QuizQuestion>) {
    if (questions.isEmpty()) return
    var index by remember { mutableIntStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    val q = questions[index % questions.size]
    SectionCard("آزمون تعاملی", "سؤال ${index + 1} از ${questions.size} — ابتدا فکر کن، بعد پاسخ را ببین", Icons.Default.Quiz) {
        BodyText(q.q)
        if (!revealed) {
            Button(onClick = { revealed = true }, Modifier.fillMaxWidth()) { Text("نمایش پاسخ") }
        } else {
            Text("پاسخ:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            BodyText(q.a)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({
                    revealed = false
                    index = (index + 1) % questions.size
                }, Modifier.weight(1f)) { Text("سؤال بعدی") }
            }
        }
    }
}

@Composable
fun NetMasterApp(vm: NetMasterViewModel) {
    val load by vm.loadState.collectAsState()
    val authenticated by vm.authenticated.collectAsState()
    when {
        !load.ready -> LoadingScreen(load) { vm.retryLoad() }
        !authenticated -> LoginScreen(vm)
        else -> MainAppShell(vm, load)
    }
}

@Composable
private fun LoadingScreen(load: AppLoadState, retry: () -> Unit) {
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (!load.ready) {
            kotlinx.coroutines.delay(1000)
            seconds += 1
        }
    }
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Hub, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(18.dp))
        Text("NetMaster Learning", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("در حال آماده‌سازی محتوای یکپارچه…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        LinearProgressIndicator(
            progress = { (load.progress.coerceIn(0, 100) / 100f) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Text("${load.progress}%  •  ${load.stage}", textAlign = TextAlign.Center)
        Text("زمان سپری‌شده: ${seconds}s", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        if (load.error != null) {
            Text(load.error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Button(onClick = retry) { Text("تلاش دوباره") }
            Spacer(Modifier.height(8.dp))
        }
        Text(
            "برنامه روی دستگاه آماده‌سازی می‌شود و کاری روی Main Thread سنگین اجرا نمی‌شود.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LoginScreen(vm: NetMasterViewModel) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var attempts by remember { mutableIntStateOf(0) }
    Column(
        Modifier.fillMaxSize().padding(26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(68.dp))
        Spacer(Modifier.height(16.dp))
        Text("ورود به NetMaster", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("دسترسی به مسیر آموزشی، Lab، شبیه‌ساز و ابزارهای مهندسی", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(22.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("نام کاربری") },
            singleLine = true
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("رمز عبور") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(Modifier.height(14.dp))
        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = {
                val ok = vm.login(username, password)
                if (!ok) {
                    attempts += 1
                    error = "نام کاربری یا رمز عبور صحیح نیست."
                }
            },
            enabled = username.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("ورود") }
        if (attempts > 0) {
            Spacer(Modifier.height(8.dp))
            Text("تلاش ناموفق: $attempts", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "احراز هویت این نسخه محلی است؛ برای انتشار عمومی، احراز هویت سمت‌سرور اضافه شود.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MainAppShell(vm: NetMasterViewModel, load: AppLoadState) {
    var tab by remember { mutableIntStateOf(0) }
    var selectedLesson by remember { mutableStateOf<Lesson?>(null) }
    val tabs = listOf(
        "خانه" to Icons.Default.Home,
        "آزمایشگاه" to Icons.Default.Science,
        "شبیه‌ساز" to Icons.Default.Terminal,
        "آزمون" to Icons.Default.Quiz,
        "AI" to Icons.Default.SmartToy,
        "عملیات" to Icons.Default.MonitorHeart,
        "جستجو" to Icons.Default.Search,
        "یادداشت" to Icons.Default.Notes,
        "مهندسی" to Icons.Default.Engineering
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectedLesson == null) "NetMaster Learning" else "درس") },
                actions = {
                    if (load.indexing) {
                        Text("${load.indexProgress}%", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.width(6.dp))
                    }
                    IconButton(onClick = { vm.logout() }) { Icon(Icons.Default.Logout, contentDescription = "خروج") }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i && selectedLesson == null,
                        onClick = { tab = i; selectedLesson = null },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 10.sp, maxLines = 1) }
                    )
                }
            }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (load.indexing) {
                LinearProgressIndicator(
                    progress = { load.indexProgress.coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "${load.stage}  •  ${load.indexSeconds}s",
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Box(Modifier.fillMaxSize()) {
                when {
                    selectedLesson != null -> LessonDetailScreen(vm, selectedLesson!!) { selectedLesson = null }
                    tab == 0 -> HomeScreen(vm) { selectedLesson = it }
                    tab == 1 -> LabScreen(vm)
                    tab == 2 -> SimScreen(vm)
                    tab == 3 -> QuizScreen(vm) { selectedLesson = it }
                    tab == 4 -> AiScreen(vm)
                    tab == 5 -> OpsScreen(vm)
                    tab == 6 -> SearchScreen(vm) { selectedLesson = it }
                    tab == 7 -> NotesScreen(vm)
                    tab == 8 -> EngineeringScreen(vm)
                }
            }
        }
    }


@Composable
fun HomeScreen(vm: NetMasterViewModel, open: (Lesson) -> Unit) {
    val c = vm.curriculum.collectAsState().value
    val progress = vm.progress.collectAsState().value
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard("NetMaster Learning", "متن‌ها برای خوانایی فارسی پالایش می‌شوند · Lab واقعی در آزمایشگاه", Icons.Default.Hub) {
                Text("درس را باز کنید. اگر متن خام تکراری باشد، نسخهٔ شفاف‌تر با مثال دقیق نمایش داده می‌شود.")
            }
        }
        if (c == null) {
            item { Text("در حال بارگذاری محتوا…", Modifier.padding(8.dp)) }
        } else {
            c.levels.forEach { level ->
                item { Text(level.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
                items(level.lessons, key = { it.id }) { lesson ->
                    Card(onClick = { open(lesson) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(lesson.title, fontWeight = FontWeight.SemiBold)
                                val m = progress[lesson.id]?.mastery
                                if (m != null) Text(masteryFa(m), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Icon(Icons.Default.ChevronLeft, null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LessonDetailScreen(vm: NetMasterViewModel, lesson: Lesson, back: () -> Unit) {
    val e = remember(lesson.id) { LessonEnricher.enrich(lesson) }
    val progress = vm.progress.collectAsState().value[lesson.id]
    val bookmarks = vm.bookmarks.collectAsState().value
    var note by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = back) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    Spacer(Modifier.width(4.dp))
                    Text("بازگشت")
                }
                IconButton(onClick = { vm.toggleBookmark(lesson.id) }) {
                    Icon(if (bookmarks.contains(lesson.id)) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, "نشانک")
                }
            }
            Text(e.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (e.wasEnriched) {
                Text(
                    "این درس از روی قالب تکراری پالایش شده تا مثال و توضیح واضح‌تر باشد.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
        if (e.goal.isNotBlank()) item { SectionCard("هدف درس", icon = Icons.Default.Flag) { BodyText(e.goal) } }
        if (e.simple.isNotBlank()) item { SectionCard("به زبان ساده", icon = Icons.Default.LightMode) { BodyText(e.simple) } }
        if (e.technical.isNotBlank()) item { SectionCard("توضیح فنی دقیق", icon = Icons.Default.Memory) { BodyText(e.technical) } }
        if (e.verification.isNotBlank()) item { SectionCard("معیار پذیرش و Verification", icon = Icons.Default.CheckCircle) { BodyText(e.verification) } }
        if (e.packetStateWalkthrough.isNotBlank()) item { SectionCard("Packet / State Walkthrough", "مسیر تغییر وضعیت یا packet را مرحله‌به‌مرحله دنبال کن", Icons.Default.Terminal) { BodyText(e.packetStateWalkthrough) } }
        if (e.failureAnalysis.isNotBlank()) item { SectionCard("تحلیل خرابی", icon = Icons.Default.Warning) { BodyText(e.failureAnalysis) } }
        if (e.expertScenario.isNotBlank()) item { SectionCard("سناریوی مهندسی", icon = Icons.Default.Engineering) { BodyText(e.expertScenario) } }
        if (e.productionDesign.isNotBlank()) item { SectionCard("طراحی تولیدی", icon = Icons.Default.Build) { BodyText(e.productionDesign) } }
        if (e.expertReference.isNotBlank()) item { SectionCard("مرجع تخصصی", icon = Icons.Default.Info) { BodyText(e.expertReference) } }
        if (e.keyPoints.isNotEmpty()) {
            item {
                SectionCard("نکات کلیدی", icon = Icons.Default.Star) {
                    e.keyPoints.forEach { Text("• $it") }
                }
            }
        }
        if (e.labSteps.isNotEmpty()) {
            item {
                SectionCard("آزمایشگاه — راهنمای اجرای واقعی", "در محیط ایزوله؛ این برنامه اتصال زنده به تجهیز ندارد", Icons.Default.Build) {
                    e.labSteps.forEachIndexed { i, s -> Text("${i + 1}. $s") }
                }
            }
            item { ProtocolFlowChart("مسیر آزمایش", e.labSteps.take(6)) }
        }
        if (e.troubleshooting.isNotEmpty()) {
            item {
                SectionCard("عیب‌یابی با فرضیه و شواهد", icon = Icons.Default.Warning) {
                    e.troubleshooting.forEach { f ->
                        Card(shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("علائم: ${f.symptom}", fontWeight = FontWeight.Bold)
                                Text("فرضیه: ${f.hypothesis}")
                                Text("شواهد: ${f.evidence}")
                                Text("قدم بعد: ${f.next}", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
        if (e.commands.isNotBlank()) {
            item {
                SectionCard("دستورات (چپ‌به‌راست)", "Cisco / MikroTik / لینوکس — جدا از متن فارسی", Icons.Default.Terminal) {
                    BodyText(e.commands, mono = true)
                }
            }
        }
        if (e.commonMistakes.isNotEmpty()) {
            item {
                SectionCard("اشتباهات رایج", icon = Icons.Default.Report) {
                    e.commonMistakes.forEach { Text("• $it") }
                }
            }
        }
        item { InteractiveQuizCard(e.quiz) }
        item {
            SectionCard("سطح تسلط شما", icon = Icons.Default.Grade) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Mastery.entries.forEach { m ->
                        FilterChip(
                            selected = progress?.mastery == m.name,
                            onClick = { vm.setMastery(lesson.id, m) },
                            label = { Text(masteryFa(m.name), fontSize = 12.sp) }
                        )
                    }
                }
            }
        }
        item {
            SectionCard("یادداشت شخصی", icon = Icons.Default.EditNote) {
                OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), minLines = 3, label = { Text("یادداشت به فارسی") })
                Button({
                    if (note.isNotBlank()) {
                        vm.addNote(lesson.id, e.title, note)
                        note = ""
                    }
                }, Modifier.fillMaxWidth()) { Text("ذخیره") }
            }
        }
    }
}
