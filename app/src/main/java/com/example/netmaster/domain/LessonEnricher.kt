package com.example.netmaster.domain

import com.example.netmaster.data.FailureCase
import com.example.netmaster.data.Lesson
import com.example.netmaster.data.QuizQuestion

/**
 * محتوای خام curriculum اغلب قالب تکراری و درهم فارسی/انگلیسی است.
 * این لایه برای نمایش: تکرار را کم می‌کند، متن را قابل‌فهم می‌کند،
 * و برای موضوعات شبکه مثال و آزمون واقعی می‌دهد.
 */
data class EnrichedLesson(
    val id: String,
    val title: String,
    val goal: String,
    val simple: String,
    val technical: String,
    val labSteps: List<String>,
    val troubleshooting: List<FailureCase>,
    val commands: String,
    val quiz: List<QuizQuestion>,
    val keyPoints: List<String>,
    val commonMistakes: List<String>,
    val tags: List<String>,
    val verification: String,
    val failureAnalysis: String,
    val packetStateWalkthrough: String,
    val expertScenario: String,
    val productionDesign: String,
    val expertReference: String,
    val wasEnriched: Boolean
)

object LessonEnricher {

    private val genericLabHints = listOf(
        "Lab حرفه‌ای", "Baseline بگیر", "فقط یک متغیر را خراب کن", "Evidence جمع کن", "Root Cause"
    )
    private val genericQuizHints = listOf(
        "کدام روش برای اثبات سلامت", "ترکیب state/configuration", "telemetry/log/counter"
    )

    fun enrich(lesson: Lesson): EnrichedLesson {
        // Current specialist lessons are authored end-to-end for one exact topic.
        // Never replace them with a generic topic bank; that was the source of
        // cross-topic corruption such as FortiGate lessons receiving DNS text.
        if (lesson.topicSpecific.isNotEmpty()) {
            return EnrichedLesson(
                id = lesson.id,
                title = cleanTitle(lesson.title),
                goal = cleanFa(lesson.goal),
                simple = cleanFa(lesson.simple),
                technical = cleanFa(lesson.technical),
                labSteps = lesson.labSteps.ifEmpty { splitSteps(lesson.lab) },
                troubleshooting = lesson.failureMatrix.ifEmpty { parseTroubleshoot(lesson.troubleshooting) },
                commands = preferCommands(lesson),
                quiz = lesson.quiz,
                keyPoints = lesson.keyPoints,
                commonMistakes = lesson.commonMistakes,
                tags = lesson.tags,
                verification = lesson.verification,
                failureAnalysis = lesson.failure_analysis,
                packetStateWalkthrough = lesson.packet_state_walkthrough.ifBlank { lesson.packetWalkthrough },
                expertScenario = lesson.expert_scenario.ifBlank { lesson.realScenario },
                productionDesign = lesson.production_design.ifBlank { lesson.configurationPlaybook },
                expertReference = lesson.expert_reference,
                wasEnriched = false
            )
        }
        val topic = detectTopic(lesson)
        val bank = topicBank[topic]
        val labBoiler = lesson.labSteps.isEmpty() && isBoilerplate(lesson.lab, genericLabHints)
        val quizBoiler = lesson.quiz.isEmpty() || lesson.quiz.any { q ->
            genericQuizHints.any { h -> q.q.contains(h) || q.a.contains(h) }
        }
        val techBoiler = isBoilerplate(lesson.technical, listOf("dependencyها، ownership و lifecycle", "Kerberos/LDAP"))

        if (bank == null && !labBoiler && !quizBoiler && !techBoiler) {
            return EnrichedLesson(
                id = lesson.id,
                title = cleanTitle(lesson.title),
                goal = cleanFa(lesson.goal),
                simple = cleanFa(lesson.simple),
                technical = if (techBoiler || lesson.technical.isBlank()) conciseTechnical(lesson) else cleanFa(lesson.technical),
                labSteps = lesson.labSteps.ifEmpty { splitSteps(lesson.lab) },
                troubleshooting = lesson.failureMatrix.ifEmpty { parseTroubleshoot(lesson.troubleshooting) },
                commands = preferCommands(lesson),
                quiz = lesson.quiz,
                keyPoints = lesson.keyPoints,
                commonMistakes = lesson.commonMistakes,
                tags = lesson.tags,
                verification = lesson.verification,
                failureAnalysis = lesson.failure_analysis,
                packetStateWalkthrough = lesson.packet_state_walkthrough.ifBlank { lesson.packetWalkthrough },
                expertScenario = lesson.expert_scenario.ifBlank { lesson.realScenario },
                productionDesign = lesson.production_design.ifBlank { lesson.configurationPlaybook },
                expertReference = lesson.expert_reference,
                wasEnriched = false
            )
        }

        val b = bank ?: genericNetworkBank(cleanTitle(lesson.title))
        return EnrichedLesson(
            id = lesson.id,
            title = cleanTitle(lesson.title),
            goal = if (lesson.goal.isBlank() || techBoiler) b.goal else cleanFa(lesson.goal),
            simple = if (isBoilerplate(lesson.simple, listOf("جزء واقعی از")) || lesson.simple.isBlank()) b.simple else cleanFa(lesson.simple),
            technical = if (techBoiler || lesson.technical.isBlank()) b.technical else cleanFa(lesson.technical),
            labSteps = if (labBoiler || lesson.labSteps.isEmpty()) b.labSteps else lesson.labSteps,
            troubleshooting = if (lesson.failureMatrix.isNotEmpty()) lesson.failureMatrix else if (isBoilerplate(lesson.troubleshooting, listOf("Failure Matrix"))) b.failures else parseTroubleshoot(lesson.troubleshooting),
            commands = if (preferCommands(lesson).isBlank() || preferCommands(lesson).length < 20) b.commands else preferCommands(lesson),
            quiz = if (quizBoiler) b.quiz else lesson.quiz,
            keyPoints = lesson.keyPoints.ifEmpty { b.keyPoints },
            commonMistakes = lesson.commonMistakes.ifEmpty { b.mistakes },
            tags = lesson.tags,
            verification = lesson.verification,
            failureAnalysis = lesson.failure_analysis,
            packetStateWalkthrough = lesson.packet_state_walkthrough.ifBlank { lesson.packetWalkthrough },
            expertScenario = lesson.expert_scenario.ifBlank { lesson.realScenario },
            productionDesign = lesson.production_design.ifBlank { lesson.configurationPlaybook },
            expertReference = lesson.expert_reference,
            wasEnriched = true
        )
    }

    private fun conciseTechnical(l: Lesson): String {
        val deep = l.deepTechnical.trim()
        if (deep.isNotBlank()) {
            val first = deep.lines().filter { it.isNotBlank() }.take(12).joinToString("\n")
            if (first.length >= 180) return cleanFa(first).take(2200)
        }
        return listOf(
            "موضوع: ${cleanTitle(l.title)}",
            "دامنه: ${l.tags.take(6).joinToString("، ")}",
            "مدل اجرا: ورودی → state/تصمیم → خروجی → verification",
            "مشاهده: حداقل یک state/configuration و یک تست end-to-end را با Expected/Actual مقایسه کن.",
            "Failure: یک فرضیه را با یک تست تفکیک‌کننده رد یا تأیید کن؛ symptom به‌تنهایی root cause نیست."
        ).joinToString("\n")
    }

    private fun preferCommands(l: Lesson) =
        listOf(l.platformCommands, l.commands, l.configurationPlaybook).firstOrNull { it.isNotBlank() }.orEmpty()

    private fun isBoilerplate(text: String, hints: List<String>): Boolean {
        if (text.isBlank()) return true
        val hits = hints.count { text.contains(it, ignoreCase = true) }
        return hits >= 2 || (hits >= 1 && text.length < 280)
    }

    private fun cleanTitle(t: String) = t.replace(Regex("\\s*—\\s*"), " — ").trim()

    /** متن نمایشی: فاصله‌های اضافه و تکرار انگلیسیِ قالب را کم می‌کند */
    fun cleanFa(text: String): String {
        var s = text.trim()
        s = s.replace(Regex("[\\t ]+"), " ")
        s = s.replace(Regex("(نباید فقط|به جای حفظ کردن)[^.]{0,80}\\."), "")
        // جدا کردن بلوک دستور از توضیح: خطوط شبیه CLI را دست نزن
        return s.trim()
    }

    private fun splitSteps(lab: String): List<String> =
        lab.lines().map { it.trim() }.filter { it.isNotBlank() && it.length > 3 }.take(8)

    private fun parseTroubleshoot(t: String): List<FailureCase> {
        if (t.isBlank()) return emptyList()
        return listOf(
            FailureCase(
                symptom = "علائم مشاهده‌شده در این موضوع",
                hypothesis = t.take(200),
                evidence = "لاگ، کانتر، show و تست end-to-end",
                next = "یک متغیر را جداگانه رد یا تأیید کن"
            )
        )
    }

    private fun detectTopic(lesson: Lesson): String {
        val blob = (lesson.title + " " + lesson.tags.joinToString(" ") + " " + lesson.id).lowercase()
        val rules = listOf(
            "vlan" to listOf("vlan", "802.1q", "trunk"),
            "tcp" to listOf("tcp", "three-way", "handshake"),
            "udp" to listOf("udp"),
            "dns" to listOf("dns", "resolve"),
            "dhcp" to listOf("dhcp", "dora"),
            "http" to listOf("http", "https", "tls"),
            "nat" to listOf("nat", "pat", "masquerade"),
            "ospf" to listOf("ospf"),
            "bgp" to listOf("bgp"),
            "stp" to listOf("stp", "rstp", "spanning"),
            "ethernet" to listOf("ethernet", "mac ", "mac address", "switch"),
            "ip" to listOf("ipv4", "subnet", "cidr", "آدرس‌دهی"),
            "routing" to listOf("routing", "route", "مسیریابی"),
            "firewall" to listOf("firewall", "acl", "فایروال"),
            "ssh" to listOf("ssh"),
            "ftp" to listOf("ftp"),
            "wifi" to listOf("wifi", "wlan", "802.11"),
            "qos" to listOf("qos", "queue", "ترافیک")
        )
        for ((id, keys) in rules) {
            if (keys.any { blob.contains(it) }) return id
        }
        return "general"
    }

    private data class Bank(
        val goal: String,
        val simple: String,
        val technical: String,
        val labSteps: List<String>,
        val failures: List<FailureCase>,
        val commands: String,
        val quiz: List<QuizQuestion>,
        val keyPoints: List<String>,
        val mistakes: List<String>
    )

    private fun genericNetworkBank(title: String) = Bank(
        goal = "بعد از این درس باید «$title» را با مثال واقعی توضیح بدهی و یک تست ساده برای صحت آن اجرا کنی.",
        simple = "$title را با یک سناریوی کوچک (دو کلاینت، یک سوئیچ/روتر) تصور کن: چه ورودی می‌آید، چه تصمیمی گرفته می‌شود، چه خروجی می‌رود.",
        technical = "۱) ورودی و پیش‌نیاز را مشخص کن.\n۲) state دستگاه (جدول، همسایه، session) را ببین.\n۳) یک تست end-to-end بزن و نتیجه را با state مقایسه کن.\n۴) اگر خراب است فقط یک فرضیه را در هر مرحله رد کن.",
        labSteps = listOf(
            "توپولوژی ساده را در آزمایشگاه/شبیه‌ساز بار کن",
            "وضعیت سالم را یادداشت کن (baseline)",
            "یک خطا تزریق کن (مثلاً قطع DNS یا فایروال)",
            "با ping/nslookup/show مسیر را پیدا کن",
            "خطا را برگردان و دوباره تست کن"
        ),
        failures = listOf(
            FailureCase("سرویس بالا نیست یا timeout", "لینک، IP، ACL یا خود سرویس", "show interface / ping / لاگ", "از لایه پایین به بالا برو"),
            FailureCase("ارتباط یک‌طرفه", "مسیر برگشت، NAT یا فایروال stateful", "traceroute دو طرفه", "هر دو جهت را چک کن")
        ),
        commands = "show ip interface brief\nping <هدف>\ntraceroute <هدف>",
        quiz = listOf(
            QuizQuestion("اولین قدم منطقی وقتی ارتباط قطع است چیست؟", "بررسی لایه فیزیکی/لینک و آدرس‌دهی محلی قبل از حدس‌های پیچیده"),
            QuizQuestion("چرا باید فقط یک متغیر را عوض کرد؟", "تا علت را قطعی ثابت کنیم؛ چند تغییر همزمان علت را مبهم می‌کند")
        ),
        keyPoints = listOf("baseline قبل از تغییر", "شواهد قبل از نتیجه‌گیری", "تست end-to-end"),
        mistakes = listOf("تغییر چند چیز با هم", "اتکا به یک دستور بدون تست واقعی")
    )

    private val topicBank: Map<String, Bank> = mapOf(
        "vlan" to Bank(
            goal = "بتوانی Access و Trunk را درست تنظیم کنی، VLAN مشترک بین سوئیچ‌ها را یکسان نگه داری و مشکل inter-VLAN را تشخیص بدهی.",
            simple = "VLAN یعنی جدا کردن شبکه‌های منطقی روی یک سوئیچ فیزیکی. سیستم‌های VLAN ۱۰ همدیگر را می‌بینند؛ برای رسیدن به VLAN ۲۰ باید روتر (L3) مسیریابی کند.",
            technical = "پورت Access فقط یک VLAN (PVID) دارد. پورت Trunk چند VLAN را با تگ 802.1Q حمل می‌کند. اگر VLAN روی یک سوئیچ تعریف شده و روی دیگری نه، یا allowed list ناقص است، ترافیک می‌افتد. Native VLAN بهتر است با کاربران یکی نباشد.",
            labSteps = listOf(
                "دو سوئیچ و یک روتر (یا L3) در نظر بگیر",
                "VLAN ۱۰ و ۲۰ را روی هر دو سوئیچ بساز (VLAN مشترک = همان شماره)",
                "پورت کلاینت را Access VLAN ۱۰ بگذار",
                "لینک بین سوئیچ‌ها را Trunk و allowed ۱۰,۲۰ کن",
                "Gateway هر VLAN را روی SVI/روتر تنظیم و ping بین VLAN را تست کن"
            ),
            failures = listOf(
                FailureCase("کلاینت IP می‌گیرد ولی به VLAN دیگر نمی‌رسد", "نبودن مسیریابی inter-VLAN یا ACL", "ping به gateway همان VLAN OK باشد؛ به VLAN دیگر fail", "SVI و route را چک کن"),
                FailureCase("دستگاه‌های دو سوئیچ هم‌VLAN یکدیگر را نمی‌بینند", "Trunk یا allowed VLAN ناقص", "show interfaces trunk / bridge vlan", "تگ و allowed را یکسان کن")
            ),
            commands = "! Cisco\nvlan 10\n name USERS\ninterface Gi1/0/10\n switchport mode access\n switchport access vlan 10\ninterface Gi1/0/1\n switchport mode trunk\n switchport trunk allowed vlan 10,20\n\n# MikroTik\n/interface bridge add name=BR1 vlan-filtering=yes\n/interface bridge port add bridge=BR1 interface=ether2 pvid=10\n/interface bridge vlan add bridge=BR1 tagged=ether1 untagged=ether2 vlan-ids=10",
            quiz = listOf(
                QuizQuestion("تفاوت اصلی پورت Access و Trunk چیست؟", "Access یک VLAN بدون تگ برای کاربر؛ Trunk چند VLAN با تگ 802.1Q بین سوئیچ‌ها"),
                QuizQuestion("چرا دو سوئیچ باید VLAN با شماره یکسان داشته باشند؟", "چون هویت VLAN همان شماره است؛ نام فقط محلی است"),
                QuizQuestion("ارتباط بین دو VLAN چگونه برقرار می‌شود؟", "با مسیریابی لایه ۳ (SVI یا روتر خارجی)، نه فقط با سوئیچ L2")
            ),
            keyPoints = listOf("VLAN مشترک = ID یکسان همه جا", "Trunk را محدود به VLAN لازم کن", "Native VLAN جدا از کاربران"),
            mistakes = listOf("فراموش کردن ساخت VLAN روی سوئیچ دوم", "باز گذاشتن همه VLANها روی Trunk")
        ),
        "tcp" to Bank(
            goal = "Handshake سه‌مرحله‌ای، تفاوت با UDP، و تشخیص قطع شدن در SYN یا بعد از Established را بلد باشی.",
            simple = "TCP قبل از ارسال داده توافق می‌کند: SYN → SYN-ACK → ACK. بعد شماره ترتیب (seq) داده را قابل‌اطمینان می‌کند. اگر سرور پورت را گوش ندهد معمولاً RST می‌آید.",
            technical = "حالت‌های مهم: SYN_SENT، ESTABLISHED، TIME_WAIT. Timeout روی SYN یعنی فیلتر/مسیر/سرویس. بعد از Established اگر داده نرود به پنجره، بافر یا مسیر برگشت نگاه کن. پورت‌های رایج: ۲۲ SSH، ۸۰ HTTP، ۴۴۳ HTTPS.",
            labSteps = listOf(
                "از کلاینت به سرور وب (پورت ۸۰ یا ۴۴۳) اتصال بزن",
                "با tcpdump/wireshark سه بسته handshake را ببین",
                "فایروال را موقتاً drop کن و ببین کجا قطع می‌شود",
                "سرویس را stop کن و RST یا timeout را مقایسه کن"
            ),
            failures = listOf(
                FailureCase("SYN می‌رود جواب نمی‌آید", "فایروال، NAT یک‌طرفه، یا سرویس down", "capture دو طرف", "مسیر و ACL را لایه به لایه"),
                FailureCase("سایت باز می‌شود ولی کند است", "retransmission، پنجره کوچک، یا loss", "شمارش retrans در capture", "کیفیت لینک و بافر")
            ),
            commands = "ss -tlnp\n# یا netstat -an | findstr LISTEN\ntcpdump -n host 192.168.20.80 and tcp port 443",
            quiz = listOf(
                QuizQuestion("ترتیب درست Handshake چیست؟", "SYN سپس SYN-ACK سپس ACK"),
                QuizQuestion("تفاوت مهم TCP با UDP؟", "TCP اتصال‌گرا و قابل‌اطمینان است؛ UDP بدون اتصال و بدون تضمین تحویل"),
                QuizQuestion("RST معمولاً چه معنی می‌دهد؟", "مقصد پورت را قبول نمی‌کند یا اتصال را قطع کرده است")
            ),
            keyPoints = listOf("بدون ACK داده قابل‌اطمینان نیست", "پورت و LISTEN را جدا از مسیر چک کن"),
            mistakes = listOf("فقط ping گرفتن برای تست سرویس TCP", "نادیده گرفتن مسیر برگشت")
        ),
        "udp" to Bank(
            goal = "بدانی UDP بدون handshake است و برای DNS/VoIP/DHCP رایج است؛ قطع شدن اغلب با timeout دیده می‌شود نه RST.",
            simple = "UDP مثل فرستادن نامه بدون رسید است: سریع است ولی اگر گم شود خودش دوباره نمی‌فرستد (مگر برنامه بالاتر این کار را بکند).",
            technical = "پورت‌های مهم: ۵۳ DNS، ۶۷/۶۸ DHCP، ۵۰۶۰ SIP. فایروال stateful برای UDP سخت‌تر است چون session واضح ندارد. در عیب‌یابی باید هم درخواست و هم پاسخ را در capture ببینی.",
            labSteps = listOf(
                "nslookup یک دامنه را بزن و بسته UDP/۵۳ را ببین",
                "DNS را در شبیه‌ساز خراب کن و timeout را مشاهده کن",
                "با tcpdump فقط udp port 53 فیلتر کن"
            ),
            failures = listOf(
                FailureCase("DNS resolve نمی‌شود", "UDP/۵۳ بسته یا سرور down", "nslookup + capture", "فایروال و forwarder")
            ),
            commands = "nslookup example.com 192.168.20.10\ntcpdump -n udp port 53",
            quiz = listOf(
                QuizQuestion("چرا UDP برای DNS مناسب است؟", "درخواست/پاسخ کوتاه است و سربار handshake نمی‌خواهد"),
                QuizQuestion("اگر پاسخ UDP نیاید چه می‌بینی؟", "معمولاً timeout در کلاینت، نه TCP RST")
            ),
            keyPoints = listOf("بدون اتصال", "برنامه بالاتر مسئول retry است"),
            mistakes = listOf("انتظار RST مثل TCP")
        ),
        "dns" to Bank(
            goal = "مسیر Resolve را از کلاینت تا سرور DNS دنبال کنی و بین مشکل شبکه و مشکل zone فرق بگذاری.",
            simple = "DNS اسم را به IP تبدیل می‌کند. اگر DNS خراب باشد، ping به IP ممکن است کار کند ولی باز کردن سایت با اسم کار نکند.",
            technical = "کلاینت به DNS سرور (مثلاً ۱۹۲.۱۶۸.۲۰.۱۰) روی UDP/۵۳ می‌پرسد. اگر recursive لازم باشد سرور به اینترنت می‌رود. SERVFAIL و timeout فرق دارند: یکی پاسخ خطاست، دیگری نرسیدن بسته.",
            labSteps = listOf(
                "با IP مستقیم سایت را تست کن (اگر ممکن است)",
                "nslookup با مشخص کردن سرور DNS",
                "در آزمایشگاه fault مربوط به DNS را تزریق کن",
                "مسیر بسته DNS را در پنل hop-by-hop ببین"
            ),
            failures = listOf(
                FailureCase("IP کار می‌کند اسم نه", "DNS", "nslookup / dig", "سرور DNS و فایروال ۵۳"),
                FailureCase("فقط بعضی اسم‌ها resolve نمی‌شوند", "zone یا forwarder", "dig +trace", "پیکربندی zone")
            ),
            commands = "nslookup example.com\ndig example.com @192.168.20.10\n# MikroTik: /ip dns print",
            quiz = listOf(
                QuizQuestion("علامت کلاسیک خرابی DNS چیست؟", "دسترسی با IP موفق و با نام دامنه ناموفق"),
                QuizQuestion("پروتکل و پورت رایج DNS؟", "عمدتاً UDP پورت ۵۳ (برای پاسخ بزرگ گاهی TCP/۵۳)")
            ),
            keyPoints = listOf("جدا کردن تست IP از تست نام", "مشخص کردن کدام DNS سرور استفاده می‌شود"),
            mistakes = listOf("فقط flush DNS بدون چک سرور")
        ),
        "dhcp" to Bank(
            goal = "چهار مرحله DORA را بشناسی و بدانی اگر Offer نیاید کجا را چک کنی.",
            simple = "کلاینت بدون IP روشن می‌شود، با Broadcast می‌پرسد، سرور IP پیشنهاد می‌دهد، کلاینت می‌پذیرد و ACK می‌گیرد.",
            technical = "Discover → Offer → Request → ACK. اگر VLAN اشتباه باشد Discover به سرور نمی‌رسد. Relay (ip helper) برای سرور دور لازم است. پورت‌ها: سرور ۶۷، کلاینت ۶۸.",
            labSteps = listOf(
                "کلاینت را برای DHCP بگذار",
                "fault DHCP را در آزمایشگاه بزن و ببین Offer نمی‌آید",
                "VLAN و relay را مرور کن",
                "lease را بعد از رفع خطا دوباره بگیر"
            ),
            failures = listOf(
                FailureCase("کلاینت APIPA می‌گیرد (۱۶۹.۲۵۴)", "Discover بدون Offer", "capture UDP ۶۷/۶۸", "سرور DHCP، VLAN، relay")
            ),
            commands = "ipconfig /renew\n# Cisco: show ip dhcp binding\n# MikroTik: /ip dhcp-server lease print",
            quiz = listOf(
                QuizQuestion("ترتیب DORA؟", "Discover, Offer, Request, ACK"),
                QuizQuestion("اگر کلاینت و سرور در دو VLAN باشند چه لازم است؟", "DHCP Relay / IP Helper")
            ),
            keyPoints = listOf("Broadcast در لایه ۲", "Relay برای سرور راه دور"),
            mistakes = listOf("فراموش کردن helper-address")
        ),
        "http" to Bank(
            goal = "مسیر HTTP/HTTPS را از TCP تا TLS و درخواست GET درک کنی.",
            simple = "HTTP روی پورت ۸۰ متن را بدون رمز می‌فرستد. HTTPS همان HTTP است روی TLS (معمولاً پورت ۴۴۳) که اول رمزنگاری را توافق می‌کند.",
            technical = "برای HTTPS: TCP handshake سپس TLS (ClientHello…) سپس HTTP. خطای certificate با خطای شبکه فرق دارد. اگر TCP برقرار نشود اصلاً به TLS نمی‌رسی.",
            labSteps = listOf(
                "سناریوی HTTPS را در آزمایشگاه انتخاب کن",
                "NAT یا فایروال را خراب کن و ببین hop کجا قرمز می‌شود",
                "با curl -v یا مرورگر نوع خطا را ببین"
            ),
            failures = listOf(
                FailureCase("صفحه سفید / timeout", "TCP یا فایروال ۴۴۳", "capture SYN", "مسیر و ACL"),
                FailureCase("هشدار گواهی", "TLS/certificate", "تاریخ و CA", "نه مشکل VLAN")
            ),
            commands = "curl -v https://example.com\n# tcpdump port 443",
            quiz = listOf(
                QuizQuestion("HTTPS روی کدام پورت رایج است؟", "۴۴۳"),
                QuizQuestion("قبل از HTTP در HTTPS چه باید موفق شود؟", "TCP و سپس TLS handshake")
            ),
            keyPoints = listOf("لایه به لایه: IP → TCP → TLS → HTTP", "خطای گواهی ≠ قطع لینک"),
            mistakes = listOf("یکی دانستن HTTP و مشکل DNS")
        ),
        "nat" to Bank(
            goal = "PAT/masquerade را برای خروج اینترنت توضیح بدهی و علائم NAT خراب را بشناسی.",
            simple = "چند دستگاه با IP خصوصی از یک IP عمومی به اینترنت می‌روند؛ روتر آدرس مبدأ را عوض می‌کند و جواب را برمی‌گرداند.",
            technical = "منبع داخل به IP عمومی ترجمه می‌شود. اگر conntrack یا rule ناترا درست نباشد، ممکن است درخواست برود و جواب برنگردد. Hairpin NAT برای دسترسی از داخل به IP عمومی لازم است.",
            labSteps = listOf(
                "سناریوی اینترنت را تست کن",
                "disable nat در شبیه‌ساز و مشاهده قطع مسیر",
                "show nat / conntrack را تفسیر کن"
            ),
            failures = listOf(
                FailureCase("درون شبکه OK اینترنت نه", "NAT یا default route", "ping 8.8.8.8 در برابر resolve DNS", "srcnat و route")
            ),
            commands = "# MikroTik\n/ip firewall nat print\n/ip firewall connection print\n! Cisco\nshow ip nat translations",
            quiz = listOf(
                QuizQuestion("هدف اصلی PAT چیست؟", "اشتراک یک IP عمومی بین چند کلاینت داخلی"),
                QuizQuestion("اگر فقط NAT خراب باشد معمولاً چه می‌شود؟", "مسیر داخلی سالم است ولی ترجمه برای اینترنت انجام نمی‌شود")
            ),
            keyPoints = listOf("ترجمه مبدأ برای خروج", "وضعیت connection را ببین"),
            mistakes = listOf("فراموش کردن masquerade روی WAN")
        ),
        "ospf" to Bank(
            goal = "همسایگی OSPF و نقش area را بدانی و علت گیر کردن در EXSTART/INIT را حدس بزنی.",
            simple = "OSPF بین روترها همسایه می‌شود و مسیرهای داخلی را خودکار رد و بدل می‌کند. اگر همسایه FULL نشود، مسیرهای OSPF نمی‌آیند.",
            technical = "نیاز به IP در یک subnet، مطابقت area، hello/dead، و گاهی MTU یکسان. حالت EXSTART اغلب به MTU یا مسدود شدن بسته مربوط است.",
            labSteps = listOf(
                "show ip ospf neighbor",
                "fault OSPF را تزریق کن",
                "area و اینترفیس را مقایسه کن"
            ),
            failures = listOf(
                FailureCase("Neighbor در FULL نیست", "area، احراز، MTU، ACL", "debug/hello", "یک پارامتر در هر بار")
            ),
            commands = "show ip ospf neighbor\nshow ip route ospf\n# MikroTik: /routing ospf neighbor print",
            quiz = listOf(
                QuizQuestion("حالت مطلوب همسایه OSPF؟", "FULL"),
                QuizQuestion("چرا MTU ناهماهنگ مشکل‌ساز است؟", "ممکن است همسایه در EXSTART بماند و دیتابیس کامل نشود")
            ),
            keyPoints = listOf("اول همسایه بعد مسیر", "area یکسان"),
            mistakes = listOf("network statement اشتباه")
        ),
        "bgp" to Bank(
            goal = "تفاوت iBGP/eBGP و اینکه BGP روی TCP/۱۷۹ است را بدانی.",
            simple = "BGP مسیر بین سامانه‌های بزرگ (یا ASها) را رد و بدل می‌کند. جلسه باید Established شود تا prefix بیاید.",
            technical = "TCP پورت ۱۷۹. Policy با route-map/filter مسیرها را کم و زیاد می‌کند. اگر session بالا نباشد، مشکل اغلب لایه زیر یا فیلتر AS است نه «خود اینترنت».",
            labSteps = listOf(
                "show bgp summary",
                "fault BGP را بزن و prefixes صفر را ببین",
                "بعد از enable دوباره Established را چک کن"
            ),
            failures = listOf(
                FailureCase("BGP Idle/Active", "IP، TCP ۱۷۹، ASN", "telnet به ۱۷۹ / log", "reachability همسایه")
            ),
            commands = "show ip bgp summary\nshow ip route bgp",
            quiz = listOf(
                QuizQuestion("BGP روی کدام پروتکل حمل می‌شود؟", "TCP پورت ۱۷۹"),
                QuizQuestion("اگر session نباشد prefix می‌آید؟", "خیر؛ اول باید Established شود")
            ),
            keyPoints = listOf("اول session بعد prefix", "policy مهم‌تر از «همه مسیرها»"),
            mistakes = listOf("تبلیغ شبکه اشتباه به اینترنت")
        ),
        "stp" to Bank(
            goal = "بدانی STP حلقه را با مسدود کردن پورت جلو می‌گیرد و تغییر توپولوژی می‌تواند ترافیک را جابه‌جا کند.",
            simple = "اگر بین سوئیچ‌ها حلقه باشد، بدون STP broadcast طوفان می‌شود. STP یک مسیر فعال می‌گذارد و بقیه را بلوکه می‌کند.",
            technical = "Root Bridge، نقش Root/Designated/Blocked. Portfast فقط روی پورت انتهایی. اگر root جابه‌جا شود ممکن است مسیر بهینه عوض شود.",
            labSteps = listOf(
                "show spanning-tree",
                "fault STP را در آزمایشگاه ببین",
                "پورت access را با portfast بررسی کن"
            ),
            failures = listOf(
                FailureCase("قطع متناوب و MAC flapping", "حلقه یا STP ناپایدار", "لاگ topology change", "کابل اضافه و root")
            ),
            commands = "show spanning-tree\nshow spanning-tree root",
            quiz = listOf(
                QuizQuestion("هدف STP؟", "جلوگیری از حلقه لایه ۲ با منطقی کردن مسیر فعال"),
                QuizQuestion("Portfast کجا مناسب است؟", "روی پورت متصل به PC/سرور نه بین سوئیچ‌ها")
            ),
            keyPoints = listOf("یک Root", "Portfast فقط لبه"),
            mistakes = listOf("خاموش کردن STP در شبکه با لینک اضافه")
        ),
        "ethernet" to Bank(
            goal = "آدرس MAC، فورواردینگ سوئیچ و تفاوت Unicast/Broadcast را درست توضیح بدهی.",
            simple = "سوئیچ با جدول MAC یاد می‌گیرد هر آدرس پشت کدام پورت است. اگر نداند، فریم را به همه پورت‌های همان VLAN می‌فرستد (flood).",
            technical = "یادگیری از آدرس مبدأ، فوروارد بر اساس مقصد. Broadcast دامنه همان VLAN است. دوبلینک MAC باعث flapping می‌شود.",
            labSteps = listOf(
                "show mac address-table",
                "دو دستگاه در یک VLAN ping کنند",
                "کابل را جابه‌جا کن و تغییر پورت در جدول را ببین"
            ),
            failures = listOf(
                FailureCase("ارتباط در همان VLAN قطع است", "VLAN پورت، کابل، یا جدول MAC", "show vlan + mac table", "پورت access درست")
            ),
            commands = "show mac address-table\nshow interfaces status",
            quiz = listOf(
                QuizQuestion("سوئیچ تصمیم فوروارد را با چه چیزی می‌گیرد؟", "آدرس MAC مقصد و جدول CAM/MAC"),
                QuizQuestion("Broadcast در لایه ۲ تا کجا می‌رود؟", "در محدوده همان VLAN (تا وقتی روتر جدا کند)")
            ),
            keyPoints = listOf("یادگیری از source MAC", "دامنه broadcast = VLAN"),
            mistakes = listOf("یکی دانستن مشکل DNS با مشکل L2")
        ),
        "firewall" to Bank(
            goal = "ترتیب rule و established/related را بفهمی و بدانی یک Drop بالای لیست همه چیز را می‌بندد.",
            simple = "فایروال مثل نگهبان است: از بالا به پایین rule را می‌خواند. اولین تطبیق معمولاً برنده است.",
            technical = "Stateful: جواب ترافیک مجاز را related می‌داند. اگر ruleِ Drop قبل از Accept باشد، سرویس قطع می‌شود. برای عیب‌یابی counter هر rule را ببین.",
            labSteps = listOf(
                "fault فایروال را در آزمایشگاه بزن",
                "مسیر HTTPS یا DNS را ببین کجا drop می‌شود",
                "rule را اصلاح و دوباره تست کن"
            ),
            failures = listOf(
                FailureCase("یک سرویس خاص بسته است", "rule order یا پورت", "counter و log", "جابه‌جایی rule")
            ),
            commands = "# MikroTik\n/ip firewall filter print\n! Cisco\nshow access-lists\nshow logging",
            quiz = listOf(
                QuizQuestion("چرا ترتیب rule مهم است؟", "چون معمولاً اولین تطبیق اعمال می‌شود و بقیه خوانده نمی‌شوند"),
                QuizQuestion("established/related چه کمکی می‌کند؟", "اجازه بازگشت ترافیک جلسهٔ مجاز بدون باز کردن همه پورت‌ها")
            ),
            keyPoints = listOf("اولویت از بالا", "لاگ و counter"),
            mistakes = listOf("accept any در انتها بدون نیاز")
        )
    )
}
