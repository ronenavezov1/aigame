package com.aigame.repository

import com.aigame.HOST_NAME
import com.aigame.objects.HostEvent
import com.aigame.speech.SpeechToText
import com.aigame.speech.isSaturated
import com.aigame.speech.pcmSamples
import com.aigame.state.AppStateManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.sqrt

private val log = LoggerFactory.getLogger("MicrophoneRepository")

// הכפתור: המסך מקליט, וכאן מקבלים את ההקלטה — מי אמר (טביעת קול) ומה
// אמר (טקסט) — ושולחים למארח. באמצע איתחול ההקלטה היא טביעת הקול של
// השחקן, והמארח לא שומע אותה.
class MicrophoneRepository(
    private val stateManager: AppStateManager,
    private val stt: SpeechToText,
    private val voiceprints: VoiceprintRepository,
    private val scope: CoroutineScope,
    private val dataDir: Path,
) {

    /** נקרא שוב ושוב כשמקש מוחזק (auto-repeat) — ולכן אידמפוטנטי. */
    fun startListening() {
        stateManager.updateEnvironment { it.copy(isMicrophoneAvailable = true) }
        stateManager.updateMicrophone { it.copy(isListening = true) }
    }

    /** המסך ניסה להקליט ולא הצליח. */
    fun microphoneUnavailable() {
        stateManager.updateEnvironment { it.copy(isMicrophoneAvailable = false) }
    }

    /** @param recording WAV, או null אם הלחיצה הייתה קצרה מדי בשביל משפט. */
    fun stopListening(recording: ByteArray?) {
        if (!stateManager.microphoneState.value.isListening) return

        // מרגע השחרור המסך מראה "חושב" — גם בשניות שבהן ההקלטה עוד הופכת לטקסט.
        stateManager.updateMicrophone { it.copy(isListening = false, isUnderstanding = recording != null) }
        if (recording != null) scope.launch {
            try {
                understand(recording)
            } finally {
                stateManager.updateMicrophone { it.copy(isUnderstanding = false) }
            }
        }
    }

    private suspend fun understand(wav: ByteArray) {
        saveLastRecording(wav)
        checkLoudness(wav)
        if (stateManager.voiceprintState.value.enrolling != null) return voiceprints.enroll(wav)

        val speaker = voiceprints.identify(wav)
        val text = if (stt.isConfigured) transcribe(wav) else null
        log.info("הקלטה: {} · שמעתי: {}", describe(wav), text?.ifBlank { null } ?: "כלום")
        stateManager.addHostEvent(if (text.isNullOrBlank()) HostEvent.Misheard else HostEvent.Heard(text.trim(), speaker))
    }

    private suspend fun transcribe(wav: ByteArray): String? = try {
        // שמות — של השחקנים ושלו — הם מה שזיהוי דיבור הכי טועה בו, ובדיוק אותם המארח צריך.
        stt.transcribe(wav, hints = stateManager.hostMemoryState.value.present.map { it.name } + HOST_NAME)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.error("זיהוי דיבור נכשל: {}", e.message)
        null
    }

    /** הקלטה רוויה — המסך מזהיר, עד שמגיעה הקלטה תקינה. */
    private fun checkLoudness(wav: ByteArray) {
        val tooLoud = isSaturated(wav)
        if (tooLoud) log.warn("ההקלטה רוויה: המיקרופון מוגבר מדי, והדיבור יוצא מעוות")
        stateManager.updateEnvironment { it.copy(isMicrophoneTooLoud = tooLoud) }
    }

    /** ההקלטה האחרונה נשמרת תמיד — אפשר לשמוע מה המיקרופון באמת קלט. */
    private fun saveLastRecording(wav: ByteArray) {
        Files.write(dataDir.resolve("last-recording.wav"), wav)
    }

    /**
     * אורך ועוצמה, ליומן: ככה רואים אם "לא הבנתי" זה שקט (המיקרופון הלא נכון)
     * או דיבור שלא הובן. דיבור רגיל יוצא בדרך כלל מעל 0.02.
     */
    private fun describe(wav: ByteArray): String = try {
        val (samples, rate) = pcmSamples(wav)
        val rms = sqrt(samples.sumOf { it.toDouble() * it } / samples.size.coerceAtLeast(1))
        "%.1f שניות, עוצמה %.3f".format(samples.size.toDouble() / rate, rms)
    } catch (e: Exception) {
        "${wav.size} בתים"
    }
}
