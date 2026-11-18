package com.aigame.speech

// ============================================================
//  טביעת קול: הקלטה נכנסת, רשימת מספרים יוצאת. שתי הקלטות של
//  אותו אדם יוצאות קרובות, של אנשים שונים — רחוקות. מי זה בפועל
//  מחליט VoiceprintRepository, לפי הטביעות שנשמרו.
//
//  בלי מודל — NoSpeakerRecognizer, וכל מי שמדבר הוא "user".
// ============================================================

interface SpeakerRecognizer {
    val isConfigured: Boolean

    /** טביעת הקול של ההקלטה (WAV), או null אם אין בה מספיק דיבור. */
    fun voiceprint(wav: ByteArray): FloatArray?
}

object NoSpeakerRecognizer : SpeakerRecognizer {
    override val isConfigured = false

    override fun voiceprint(wav: ByteArray): FloatArray? = null
}
