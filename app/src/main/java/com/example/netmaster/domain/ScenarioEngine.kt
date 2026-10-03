package com.example.netmaster.domain

import com.example.netmaster.data.EngineeringScenario
import com.example.netmaster.data.EngineeringScenarioHit

data class ScenarioExplanation(
    val scenarioId: String,
    val score: Float,
    val matchedFields: List<String>,
    val matchedTokens: List<String>,
    val missingSignals: List<String>,
    val nextTest: String
)

class ScenarioEngine(private val scenarios: List<EngineeringScenario>) {
    fun retrieve(query: String, topK: Int = 8): List<EngineeringScenarioHit> {
        val qTokens = tokenize(query)
        if (qTokens.isEmpty()) return emptyList()
        return scenarios.mapNotNull { scenario ->
            scoreScenario(scenario, qTokens)?.let { EngineeringScenarioHit(scenario, it) }
        }.sortedWith(compareByDescending<EngineeringScenarioHit> { it.score }.thenBy { it.scenario.id }).take(topK.coerceIn(1,50))
    }

    fun explain(scenario: EngineeringScenario, query: String): ScenarioExplanation {
        val queryTokens=tokenize(query).distinct()
        val fieldTokens=mapOf(
            "domain" to tokenize(scenario.domain),
            "title" to tokenize(scenario.title),
            "symptom" to tokenize(scenario.symptom),
            "scope" to tokenize(scenario.scope),
            "hypothesis" to tokenize(scenario.hypotheses.joinToString(" ")),
            "evidence" to tokenize(scenario.evidence.joinToString(" ")),
            "test" to tokenize(scenario.tests.joinToString(" ")),
            "tag" to tokenize(scenario.tags.joinToString(" "))
        )
        val matched=queryTokens.filter { q -> fieldTokens.values.any { toks -> toks.any { it == q || it.startsWith(q) || q.startsWith(it) } } }
        val matchedFields=fieldTokens.filterValues { toks -> queryTokens.any { q -> toks.any { it == q || it.startsWith(q) || q.startsWith(it) } } }.keys.toList()
        val missing=when {
            scenario.evidence.isEmpty() -> listOf("evidence")
            scenario.tests.isEmpty() -> listOf("discriminating test")
            scenario.rollback.isBlank() -> listOf("rollback")
            else -> emptyList()
        }
        return ScenarioExplanation(
            scenarioId=scenario.id,
            score=scoreScenario(scenario, queryTokens) ?: 0f,
            matchedFields=matchedFields,
            matchedTokens=matched,
            missingSignals=missing,
            nextTest=scenario.tests.firstOrNull().orEmpty()
        )
    }

    fun topByDomain(domain: String, limit: Int = 10): List<EngineeringScenario> = scenarios
        .filter { normalize(it.domain)==normalize(domain) || normalize(it.tags.joinToString(" ")).contains(normalize(domain)) }
        .sortedWith(compareByDescending<EngineeringScenario> { it.evidence.size + it.tests.size }.thenBy { it.title })
        .take(limit.coerceIn(1,50))

    fun domains(): List<String> = scenarios.map { it.domain.trim() }.filter { it.isNotBlank() }.distinct().sorted()

    private fun scoreScenario(scenario: EngineeringScenario, qTokens: List<String>): Float? {
        if(qTokens.isEmpty()) return null
        val fields=listOf(
            "domain" to scenario.domain,
            "title" to scenario.title,
            "symptom" to scenario.symptom,
            "scope" to scenario.scope,
            "hypotheses" to scenario.hypotheses.joinToString(" "),
            "evidence" to scenario.evidence.joinToString(" "),
            "tests" to scenario.tests.joinToString(" "),
            "tags" to scenario.tags.joinToString(" ")
        )
        var matched=0f
        val weights=mutableListOf<Float>()
        qTokens.distinct().forEach { q ->
            val best=fields.maxOf { (name,text) ->
                val toks=tokenize(text)
                when {
                    q in toks -> when(name) { "title"->1f; "domain"->.9f; "symptom"->.8f; "tags"->.7f; else->.45f }
                    toks.any { it.startsWith(q) || q.startsWith(it) } -> .25f
                    else -> 0f
                }
            }
            matched+=if(best>0f)1f else 0f
            weights+=best
        }
        val uniqueQ=qTokens.distinct().size.coerceAtLeast(1)
        val coverage=matched/uniqueQ
        val weighted=weights.sum()/uniqueQ
        val exact=normalize(scenario.title).contains(normalize(qTokens.joinToString(" ")))
        val phraseBonus=when {
            exact -> .24f
            normalize(scenario.symptom).contains(normalize(qTokens.joinToString(" "))) -> .20f
            normalize(scenario.domain).contains(normalize(qTokens.joinToString(" "))) -> .15f
            else -> 0f
        }
        val score=(coverage*.55f+weighted*.35f+phraseBonus).coerceIn(0f,1f)
        return score.takeIf { it>.05f }
    }

    private fun normalize(text:String)=text.lowercase().replace('ي','ی').replace('ك','ک').replace('ة','ه')
        .replace(Regex("[\\u064B-\\u065F]")," ").replace(Regex("[^\\p{L}\\p{N}._:/-]+")," ").trim()
    private fun tokenize(text:String)=normalize(text).split(Regex("\\s+")).filter { it.length>1 }
}
