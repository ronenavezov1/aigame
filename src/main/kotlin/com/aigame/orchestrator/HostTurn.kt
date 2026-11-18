package com.aigame.orchestrator

import com.aigame.objects.Player
import com.aigame.objects.SpeakerGuess
import com.aigame.speech.SpeechStyle
import com.aigame.state.Expression

// ============================================================
//  תור אחד של הסוכן: זיכרון (ב-context) + קלט → פלט.
//  HostOrchestrator לא זוכר כלום בין תורות — מה שהוא החליט על
//  המסך ועל הקול יוצא כאן, והרפוזיטורי מיישם את זה.
// ============================================================

/** מה שקרה עכשיו. */
sealed interface HostInput {
    /** מישהו לחץ על הכפתור ואמר את זה (או הקליד בפאנל). speaker — מה זיהוי הקול חושב. */
    data class Heard(val text: String, val speaker: SpeakerGuess? = null) : HostInput

    /** הסוכן התעורר לבד. השחקנים כבר נבחרו בהגרלה. */
    data class WakeUp(val chosen: List<Player>) : HostInput
}

/** מה שהסוכן החליט להגיד, ואיך. */
data class HostOutput(
    val speech: String,                         // מה שהוא אומר בקול
    val expression: Expression = Expression.NEUTRAL,
    val style: SpeechStyle = SpeechStyle(),     // איך להגיד: טון, ואיזה מודל קול
    val speaker: String? = null,                // מי דיבר, אם הסוכן תיקן את זיהוי הקול
)

/** אוסף את מה שהפעולות של הסוכן ביקשו במהלך תור אחד. נבנה לכל תור ונזרק בסופו. */
class HostOutputBuilder {
    var expression: Expression = Expression.NEUTRAL
    var style: SpeechStyle = SpeechStyle()
    var speaker: String? = null

    fun build(speech: String) = HostOutput(speech, expression, style, speaker)
}
