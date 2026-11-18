package com.aigame.speech

// ============================================================
//  זיהוי דיבור: WAV נכנס, טקסט בעברית יוצא.
//
//  בלי ספק מוגדר — NoSpeechToText, ומקלידים בפאנל (F1). ההקלדה
//  עוברת בדיוק באותו מסלול כמו דיבור.
// ============================================================

interface SpeechToText {
    val isConfigured: Boolean

    /**
     * הטקסט שנאמר, או null אם לא הובן כלום.
     *
     * @param hints מילים שסביר שיופיעו — שמות השחקנים. שמות הם מה
     *              שזיהוי דיבור הכי טועה בו, ובמסיבה הם הכי חשובים.
     */
    suspend fun transcribe(wav: ByteArray, hints: List<String> = emptyList()): String?
}

object NoSpeechToText : SpeechToText {
    override val isConfigured = false

    override suspend fun transcribe(wav: ByteArray, hints: List<String>): String? = null
}
