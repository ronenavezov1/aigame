package com.aigame.state

// מה שהמארח עושה ומראה עכשיו. ב-RAM בלבד — אין טעם להתעורר באמצע משפט שנקטע.
data class HostState(
    val phase: Phase = Phase.SLEEPING,
    val expression: Expression = Expression.NEUTRAL,
    // מה שכתוב על המסך
    val subtitle: String? = null,
    // המשפט שהמסך צריך להקריא עכשיו. null = המארח לא מדבר.
    val utterance: Utterance? = null,
)

/**
 * משפט אחד של המארח: הטקסט, והאודיו שלו (null = אין קול, רק כתובית).
 * לא data class בכוונה — כל משפט הוא אובייקט חדש, גם כשהטקסט חוזר על עצמו,
 * וככה המסך יודע שצריך להקריא שוב.
 */
class Utterance(val text: String, val audio: ByteArray?)

enum class Phase {
    SLEEPING, // עיניים סגורות. מחכה לכפתור או להתעוררות.
    AWAKE,    // ער, לא עושה כלום. חוזר לישון אחרי כמה שניות.
    THINKING,
    SPEAKING,
}

// ההבעות שהמארח יכול לבחור לעצמו. השמות הם מה שהמודל רואה.
enum class Expression {
    NEUTRAL,
    HAPPY,
    LAUGHING,
    EVIL,
    SUSPICIOUS,
    SURPRISED,
    BORED;

    companion object {
        fun parse(text: String): Expression? = entries.firstOrNull { it.name.equals(text.trim(), ignoreCase = true) }
    }
}
