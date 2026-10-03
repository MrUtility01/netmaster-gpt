package com.example.netmaster.ai

import com.example.netmaster.data.Lesson

data class AiEvidence(val type: String, val value: String)
data class AiHypothesis(
    val title: String,
    val confidence: Int,
    val evidenceToCheck: List<String>
)
data class AiResponse(
    val title: String,
    val answer: String,
    val hypotheses: List<AiHypothesis> = emptyList(),
    val nextTests: List<String> = emptyList(),
    val safetyNotes: List<String> = emptyList()
)

enum class AiMode {
    TEACHER, SOCRATIC, TROUBLESHOOTER, EXAMINER, LAB_COACH, CONFIG_REVIEWER, AUTONOMOUS_COACH
}

interface AiProvider {
    suspend fun ask(mode: AiMode, prompt: String, context: List<Lesson> = emptyList()): AiResponse
}

class OfflineAiProvider : AiProvider {
    private fun groundedPrompt(prompt: String, context: List<Lesson>): String {
        val ctx = context.take(8).joinToString("\n\n") { lesson ->
            val failures = lesson.failureMatrix.joinToString("; ") { f ->
                f.symptom + " => " + f.next
            }
            buildString {
                append("[SOURCE ").append(lesson.id).append("] ").append(lesson.title).append('\n')
                append(lesson.deepTechnical).append('\n')
                append(lesson.packetWalkthrough).append('\n')
                append(lesson.configurationPlaybook).append('\n')
                append(lesson.troubleshooting).append('\n')
                append(failures)
            }
        }
        return "Knowledge-grounded context:\n$ctx\n\nUser request:\n$prompt"
    }

    override suspend fun ask(mode: AiMode, prompt: String, context: List<Lesson>): AiResponse {
        val grounded = groundedPrompt(prompt, context)
        val related = context.take(4).joinToString(", ") { it.title }
        return when (mode) {
            AiMode.TEACHER -> AiResponse(
                title = "استاد NetMaster",
                answer = "$grounded\n\nموضوع را با ترتیب Concept -> Architecture -> Internals -> Packet/State -> Configuration -> Verification توضیح بده. منابع مرتبط: $related"
            )
            AiMode.SOCRATIC -> AiResponse(
                title = "راهنمای سقراطی",
                answer = "$grounded\n\nبه‌جای حدس یک سؤال قابل‌آزمایش انتخاب کن: Scope چیست؟ کدام Layer محتمل است؟ چه Evidence کم‌هزینه‌ای داری؟"
            )
            AiMode.EXAMINER -> AiResponse(
                title = "آزمون",
                answer = "$grounded\n\nبدون نگاه به پاسخ، Symptom، Hypothesis، Evidence و اولین Test کم‌خطر را بنویس؛ سپس نتیجه را با واقعیت بسنج."
            )
            AiMode.TROUBLESHOOTER, AiMode.AUTONOMOUS_COACH -> AiResponse(
                title = "Troubleshooting Coach",
                answer = "$grounded\n\nاز Interface/Link شروع کن، سپس ARP/ND، Routing، DNS، Firewall/NAT و Application. منابع: $related",
                hypotheses = listOf(
                    AiHypothesis("L2/L3 fault", 70, listOf("interface status", "arp", "route")),
                    AiHypothesis("DNS/Firewall", 55, listOf("nslookup", "acl counters"))
                ),
                nextTests = listOf("ping gateway", "traceroute", "check logs"),
                safetyNotes = listOf("تغییر production فقط با change window")
            )
            AiMode.LAB_COACH -> AiResponse(
                title = "Lab Coach",
                answer = "$grounded\n\nLab را به Baseline -> Fault Injection -> Capture/Logs -> Root Cause -> Fix -> Rollback تقسیم کن."
            )
            AiMode.CONFIG_REVIEWER -> AiResponse(
                title = "Config Reviewer",
                answer = "$grounded\n\nDiff را بر اساس intent بررسی کن: security impact، routing impact، blast radius و rollback plan."
            )
        }
    }
}
