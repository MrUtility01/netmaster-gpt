package com.example.netmaster.search

import com.example.netmaster.data.Lesson

data class SearchHit(val lesson: Lesson, val score: Int, val matched: List<String>)

class SearchEngine {
    fun search(lessons: List<Lesson>, query: String): List<SearchHit> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return emptyList()
        return lessons.mapNotNull { l ->
            val fields = linkedMapOf(
                "عنوان" to l.title, "هدف" to l.goal, "مفهوم" to l.simple, "فنی" to l.technical,
                "Command" to l.commands, "Traffic" to l.traffic, "Lab" to l.lab, "Troubleshooting" to l.troubleshooting,
                "Tag" to l.tags.joinToString(" "), "نکات" to l.keyPoints.joinToString(" ")
            )
            val matched = fields.filterValues { it.lowercase().contains(q) }.keys.toList()
            val exactTitle = if (l.title.lowercase().contains(q)) 30 else 0
            val score = exactTitle + matched.size * 10
            if (score == 0) null else SearchHit(l, score, matched)
        }.sortedByDescending { it.score }
    }
}
