package com.aigame.repository

import com.aigame.objects.Player
import com.aigame.objects.SpeakerGuess
import com.aigame.speech.SpeakerRecognizer
import com.aigame.speech.isSaturated
import com.aigame.speech.pitchHz
import com.aigame.speech.voiceGender
import com.aigame.state.AppStateManager
import com.aigame.storage.VoiceprintStorage
import org.slf4j.LoggerFactory
import kotlin.math.sqrt

private val log = LoggerFactory.getLogger("VoiceprintRepository")

// כמה דומות הטביעות צריכות להיות כדי להגיד "זה הוא". 1 = זהות.
// קולות של אנשים שונים יוצאים בדרך כלל מתחת ל-0.4.
private const val SAME_SPEAKER = 0.5f

// השני במקום קרוב לראשון עד כדי זה — אולי זה בכלל הוא. שני קולות דומים
// הם הדרך שבה זיהוי טועה בביטחון, והסוכן צריך לדעת.
private const val CLOSE_CALL = 0.1f

// איתחול צריך מספיק דיבור: לפחות 4 שניות (16000 דגימות בשנייה × 2 בתים).
private const val MIN_ENROLL_BYTES = 4 * 16_000 * 2

// ============================================================
//  טביעות הקול: איתחול לכל שחקן, וזיהוי מי מדבר.
//
//    startEnrollment(שם)   המסך מבקש מהשחקן להקריא טקסט
//    enroll(הקלטה)         ההקלטה הזו הופכת לטביעה שלו — ל-state ולקובץ
//    identify(הקלטה)       מי מהרשומים הכי קרוב, אם מספיק קרוב — ואם אף
//                          אחד, אולי בדרך השלילה (אישה/גבר, בלי טביעה)
// ============================================================

class VoiceprintRepository(
    private val stateManager: AppStateManager,
    private val recognizer: SpeakerRecognizer,
    private val storage: VoiceprintStorage,
) {
    /** בהדלקה: הטביעות מהקובץ נכנסות ל-state. */
    fun load() = stateManager.updateVoiceprint { it.copy(voiceprints = storage.load()) }

    fun startEnrollment(name: String) = stateManager.updateVoiceprint { it.copy(enrolling = name, problem = null) }

    fun cancelEnrollment() = stateManager.updateVoiceprint { it.copy(enrolling = null, problem = null) }

    /** ההקלטה של מי שעושה עכשיו איתחול. נכשל — נשאר באיתחול, עם הסבר למסך. */
    fun enroll(wav: ByteArray) {
        val name = stateManager.voiceprintState.value.enrolling ?: return
        if (wav.size < MIN_ENROLL_BYTES) {
            val seconds = "%.1f".format(wav.size / (16_000 * 2.0))
            return problem(name, "ההקלטה קצרה מדי ($seconds שניות) — צריך להקריא את כל הטקסט, לפחות 4 שניות.")
        }
        if (isSaturated(wav)) {
            // טביעה מהקלטה רוויה דומה לטביעות של כולם — והזיהוי מתבלבל בין אנשים.
            return problem(name, "המיקרופון חזק מדי וההקלטה יצאה מעוותת. הנמך את עוצמת הקלט בהגדרות הצליל ונסה שוב.")
        }
        val vector = voiceprintOf(wav) ?: return problem(name, "לא הצלחתי להפיק טביעת קול. נסה שוב, קרוב יותר למיקרופון.")

        stateManager.updateVoiceprint {
            it.copy(voiceprints = it.voiceprints + (name to vector.toList()), enrolling = null, problem = null)
        }
        storage.save(stateManager.voiceprintState.value.voiceprints)
        log.info("טביעת קול נשמרה: {}", name)
    }

    /** מי מדבר בהקלטה — או null אם אף אחד מהרשומים שהגיעו לא מספיק קרוב. */
    fun identify(wav: ByteArray): SpeakerGuess? {
        // רק מי שהגיע: פחות קולות להשוות — פחות טעויות.
        val here = stateManager.hostMemoryState.value.present
        val voiceprints = stateManager.voiceprintState.value.voiceprints.filterKeys { name -> here.any { it.name == name } }
        val vector = if (voiceprints.isEmpty()) null else voiceprintOf(wav)

        if (vector != null) {
            val ranked = voiceprints.map { (name, known) -> name to similarity(vector, known) }.sortedByDescending { it.second }
            log.info("זיהוי קול: {}", ranked.joinToString { (name, score) -> "$name %.2f".format(score) })

            val (name, score) = ranked.first()
            if (score >= SAME_SPEAKER) {
                val maybe = ranked.getOrNull(1)?.takeIf { (_, second) -> score - second < CLOSE_CALL }?.first
                return SpeakerGuess(name, score, maybe)
            }
        }
        return byElimination(wav, here.filter { it.name !in voiceprints })
    }

    /**
     * אף אחד מהרשומים לא דומה מספיק. אם הקול של אישה (או של גבר), ומבין מי
     * שנמצא פה בלי טביעת קול יש רק אחד כזה — כנראה שזה הוא. למשל מי שלא רצה
     * לתת טביעה.
     */
    private fun byElimination(wav: ByteArray, withoutVoiceprint: List<Player>): SpeakerGuess? {
        val pitch = try {
            pitchHz(wav)
        } catch (e: Exception) {
            null
        }
        val voice = voiceGender(pitch)
        val only = voice?.let { gender -> withoutVoiceprint.filter { it.address == gender }.singleOrNull() }
        log.info(
            "קול לא מוכר: {} הרץ ({}) → {}",
            pitch?.let { "%.0f".format(it) } ?: "?", voice?.hebrew ?: "לא בטוח", only?.name ?: "לא יודע מי",
        )
        return only?.let { SpeakerGuess(it.name, score = 0f, byVoice = voice) }
    }

    /** הקלטה פגומה לא מפילה את המסיבה — פשוט אין טביעה. */
    private fun voiceprintOf(wav: ByteArray): FloatArray? = try {
        recognizer.voiceprint(wav)
    } catch (e: Exception) {
        log.error("טביעת קול נכשלה: {}", e.message)
        null
    }

    private fun problem(name: String, text: String) {
        log.warn("איתחול של {} לא נשמר: {}", name, text)
        stateManager.updateVoiceprint { it.copy(problem = text) }
    }
}

/** כמה שתי טביעות דומות: 1 = אותו כיוון בדיוק, 0 = אין קשר. */
internal fun similarity(a: FloatArray, b: List<Float>): Float {
    var dot = 0.0
    var normA = 0.0
    var normB = 0.0
    for (i in a.indices) {
        dot += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }
    return if (normA == 0.0 || normB == 0.0) 0f else (dot / sqrt(normA * normB)).toFloat()
}
