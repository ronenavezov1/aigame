package com.aigame.speech

import com.aigame.objects.Address
import kotlin.math.sqrt

// ============================================================
//  קול של אישה או של גבר — לפי גובה הקול (התדר היסודי).
//
//  נמדד כאן: קולות נשיים ~188 הרץ, גבריים 137–155 (כולל הקלטה אמיתית
//  של אחד החבר'ה). הסף זהיר: באמצע — "לא בטוח". עדיף "לא יודע" מאשר
//  לקרוא לגבר בשם של בת.
// ============================================================

private const val FEMALE_HZ = 175f
private const val MALE_HZ = 160f

/** זכר / נקבה לפי גובה הקול, או null אם לא בטוח. */
fun voiceGender(pitchHz: Float?): Address? = when {
    pitchHz == null -> null
    pitchHz >= FEMALE_HZ -> Address.FEMALE
    pitchHz <= MALE_HZ -> Address.MALE
    else -> null
}

/** הגובה החציוני של הדיבור בהקלטה (WAV), או null אם אין בה מספיק דיבור. */
fun pitchHz(wav: ByteArray): Float? {
    val (x, rate) = pcmSamples(wav)
    val frame = rate * 40 / 1000
    val hop = rate * 20 / 1000
    val minLag = rate / 400 // 400 הרץ — הכי גבוה שבודקים
    val maxLag = rate / 70  // 70 הרץ — הכי נמוך
    val starts = (0 until x.size - frame - maxLag step hop).toList()
    if (starts.isEmpty()) return null

    // רק מסגרות של דיבור: מספיק חזקות יחסית לחלקים הרועשים בהקלטה.
    val energy = starts.map { rms(x, it, frame) }
    val loud = energy.sorted()[(energy.size * 0.9).toInt()]
    val pitches = starts.indices
        .filter { energy[it] >= maxOf(0.01f, loud * 0.3f) }
        .mapNotNull { framePitch(x, starts[it], frame, minLag, maxLag, rate) }
        .sorted()
    if (pitches.size < 10) return null
    return pitches[pitches.size / 2]
}

/** הגובה של מסגרת אחת, לפי אוטוקורלציה. null = לא קולי (לחישה, רעש). */
private fun framePitch(x: FloatArray, start: Int, frame: Int, minLag: Int, maxLag: Int, rate: Int): Float? {
    val corr = FloatArray(maxLag + 1)
    var e0 = 0.0
    for (i in 0 until frame) e0 += x[start + i] * x[start + i]
    for (lag in minLag..maxLag) {
        var num = 0.0
        var e1 = 0.0
        for (i in 0 until frame) {
            val a = x[start + i]
            val b = x[start + i + lag]
            num += a * b
            e1 += b * b
        }
        corr[lag] = if (e0 == 0.0 || e1 == 0.0) 0f else (num / sqrt(e0 * e1)).toFloat()
    }
    val best = (minLag..maxLag).maxOf { corr[it] }
    if (best < 0.5f) return null
    // השיא הראשון שגבוה כמעט כמו הגבוה ביותר — אחרת לפעמים נתפס פי שניים מהמחזור,
    // והקול נמדד נמוך באוקטבה. משם מטפסים לפסגה שלו, לא נעצרים באמצע העלייה.
    var lag = (minLag..maxLag).first { corr[it] >= 0.9f * best }
    while (lag < maxLag && corr[lag + 1] > corr[lag]) lag++
    return rate.toFloat() / lag
}

private fun rms(x: FloatArray, start: Int, length: Int): Float {
    var sum = 0.0
    for (i in start until start + length) sum += x[i] * x[i]
    return sqrt(sum / length).toFloat()
}
