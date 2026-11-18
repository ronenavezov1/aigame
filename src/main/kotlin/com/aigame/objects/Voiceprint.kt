package com.aigame.objects

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// טביעת הקול של שחקן: רשימת מספרים שהמודל מפיק מההקלטה שלו.
// נשמרת ב-voices.json, ולכן כל שחקן עושה איתחול פעם אחת.
@Serializable
data class Voiceprint(
    @SerialName("שם") val name: String,
    @SerialName("טביעה") val vector: List<Float>,
)

// מה זיהוי הקול חושב: מי מדבר, וכמה הטביעות דומות (0..1).
// maybe — שחקן שהקול שלו קרוב מדי, כך שאולי זה בכלל הוא.
// byVoice — ניחוש בדרך השלילה: הקול לא מוכר, אבל הוא של אישה (או גבר), ורק
// שחקן אחד כזה נמצא פה בלי טביעת קול. score אז לא רלוונטי.
data class SpeakerGuess(
    val name: String,
    val score: Float,
    val maybe: String? = null,
    val byVoice: Address? = null,
)
