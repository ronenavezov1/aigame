package com.aigame.speech

import kotlin.math.abs

// ============================================================
//  הקלטה רוויה: המיקרופון מוגבר מדי, והגלים נחתכים בתקרה.
//
//  ככה נשמע מיקרופון בכניסה הלא נכונה, או עם עוצמת קלט במקסימום: הדיבור
//  יוצא מעוות, כל רעש רקע נהיה שאגה, וזיהוי הדיבור וטביעות הקול טועים.
//  בהקלטה כזו שנמדדה כאן כ-17% מהדגימות היו בתקרה. בהקלטה תקינה — כמעט אף אחת.
// ============================================================

// דגימה שקרובה לעוצמה המלאה עד כדי זה — נחתכה.
private const val CEILING = 0.98f

// עיצור חזק או צחוק נוגעים בתקרה לרגע. הקלטה רוויה יושבת שם.
private const val SATURATED_SHARE = 0.01f

/** האם ההקלטה (WAV) רוויה. הקלטה שאי אפשר לקרוא — לא. */
fun isSaturated(wav: ByteArray): Boolean {
    val samples = try {
        pcmSamples(wav).first
    } catch (e: Exception) {
        return false
    }
    if (samples.isEmpty()) return false
    return samples.count { abs(it) >= CEILING } >= samples.size * SATURATED_SHARE
}
