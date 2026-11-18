package com.aigame.view.devices

import kotlin.math.sqrt

/**
 * עוצמה 0..1 של PCM בן 16 ביט little-endian.
 *
 * אותו חישוב משמש את טבעת ההקשבה (מהמיקרופון) ואת הפה (מהקול של
 * המארח). RMS ולא שיא: שיא קופץ על כל עיצור, RMS נראה כמו דיבור.
 * מוגבר פי 4 כי דיבור רגיל רחוק מאוד מהעוצמה המלאה.
 */
internal fun pcmLevel(buf: ByteArray, n: Int): Float {
    var sum = 0.0
    var i = 0
    while (i + 1 < n) {
        val sample = (buf[i].toInt() and 0xFF) or (buf[i + 1].toInt() shl 8)
        sum += sample.toDouble() * sample
        i += 2
    }
    val rms = sqrt(sum / (n / 2).coerceAtLeast(1)) / 32768.0
    return (rms * 4).toFloat().coerceIn(0f, 1f)
}
