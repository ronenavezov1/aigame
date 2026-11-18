package com.aigame.objects

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// הדוגמאות שאתה כותב לסוכן (src/main/resources): משימות, ומשפטים וסלנג באופי שלו.
data class HostExamples(
    val tasks: List<TaskExample> = emptyList(),
    val style: StyleExamples = StyleExamples(),
)

// משימה לדוגמה — שורה ב-task-examples.json.
@Serializable
data class TaskExample(
    @SerialName("משימה") val text: String,
    // למי: מספר ("2"), או מילים ("כולם", "כל הבנים").
    @SerialName("שחקנים") val players: String = "1",
) {
    /** איך זה נקרא לסוכן: "לשחקן אחד", "ל-3 שחקנים", "כל הבנים". */
    val forWhom: String
        get() = players.trim().toIntOrNull()?.let { if (it == 1) "לשחקן אחד" else "ל-$it שחקנים" } ?: players.trim()
}

// איך הוא נשמע — style-examples.json.
@Serializable
data class StyleExamples(
    @SerialName("משפטים") val lines: List<String> = emptyList(),
    @SerialName("סלנג") val slang: List<String> = emptyList(),
)
