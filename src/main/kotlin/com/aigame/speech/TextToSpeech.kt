package com.aigame.speech

// טקסט → אודיו. רק ממיר: הניגון עצמו הוא של הרמקול, בצד של המסך.
interface TextToSpeech {
    /** WAV של הטקסט, או null אם אין קול בכלל — ואז המסך מציג רק כתובית. */
    suspend fun synthesize(text: String, style: SpeechStyle = SpeechStyle()): ByteArray?
}

/** איך להגיד משפט — המנצח מחליט על זה לכל משפט. */
data class SpeechStyle(
    val voice: SpeechVoice = SpeechVoice.PUCK,
    /** הוראת משחק קצרה, למשל "whispering, like a villain". ריק = טון רגיל. */
    val delivery: String = "",
)

/** הקולות שהמארח יכול לבחור. התיאור הוא מה שהמנצח רואה. */
enum class SpeechVoice(val description: String) {
    CHARON_PRO("עמוק ועשיר, הכי איכותי — אבל מוסיף בערך שתי שניות המתנה. לרגעים גדולים."),
    PUCK("קליל ושמח. ברירת המחדל."),
    FENRIR("נרגש ומלא אנרגיה. להתלהבות."),
    ;

    companion object {
        fun parse(text: String): SpeechVoice? = entries.firstOrNull { it.name.equals(text.trim(), ignoreCase = true) }
    }
}

/** בלי ספק קול: אין אודיו, רק כתובית. */
object NoTextToSpeech : TextToSpeech {
    override suspend fun synthesize(text: String, style: SpeechStyle): ByteArray? = null
}
