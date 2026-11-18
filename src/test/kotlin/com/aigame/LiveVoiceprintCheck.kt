package com.aigame

import com.aigame.repository.VoiceprintRepository
import com.aigame.repository.similarity
import com.aigame.speech.GeminiApi
import com.aigame.speech.GeminiTextToSpeech
import com.aigame.speech.SherpaSpeakerRecognizer
import com.aigame.speech.SpeechStyle
import com.aigame.speech.SpeechVoice
import com.aigame.state.AppStateManager
import com.aigame.storage.VoiceprintStorage
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * טביעת קול אמיתית: המודל המקומי, ושלושה קולות של Gemini בתור "שחקנים".
 * כל קול עושה איתחול עם משפט אחד, ואז מדפיסים כמה כל משפט אחר דומה לכל אחד.
 *
 * בודקת שהצנרת עובדת — ספרייה מקומית, מודל, טביעות — ולא את הדיוק: קולות
 * סינתטיים של אותו מודל דומים זה לזה הרבה יותר מאנשים אמיתיים (PUCK ו-FENRIR
 * יצאו 0.74–0.79). את הדיוק בודקים עם החבר'ה.
 *
 * רק עם LIVE_VOICEPRINT_CHECK=1, עם GOOGLE_API_KEY ב-.env ועם המודל ב-models/.
 *
 *   LIVE_VOICEPRINT_CHECK=1 ./gradlew test --tests '*LiveVoiceprintCheck*'
 */
class LiveVoiceprintCheck {

    @Test
    fun recognizesWhoIsTalking() = runBlocking {
        if (System.getenv("LIVE_VOICEPRINT_CHECK") == null) return@runBlocking

        val tts = GeminiTextToSpeech(GeminiApi(Env.get("GOOGLE_API_KEY")!!))
        val recognizer = SherpaSpeakerRecognizer(Path.of("models/wespeaker_en_voxceleb_resnet34_LM.onnx"))
        val appStateManager = AppStateManager()
        val voices = VoiceprintRepository(appStateManager, recognizer, VoiceprintStorage(Files.createTempDirectory("live-voice")))

        val players = mapOf("רוני" to SpeechVoice.PUCK, "דנה" to SpeechVoice.CHARON_PRO, "אורן" to SpeechVoice.FENRIR)
        val setup = "אני מצהיר בזאת, בפני השופט העליון של הבית, שאני כאן מרצוני החופשי, ושכל צ'ייסר חייב להישתות."
        players.forEach { (name, voice) ->
            voices.startEnrollment(name)
            voices.enroll(tts.synthesize(setup, SpeechStyle(voice)))
        }
        val enrolled = appStateManager.voiceprintState.value.voiceprints
        assertEquals(players.keys, enrolled.keys)

        val lines = mapOf("רוני" to "אני רוצה עוד בירה, בבקשה", "דנה" to "מי לקח לי את הכוס?", "אורן" to "בואו נשחק עוד סיבוב")
        lines.forEach { (name, line) ->
            val wav = tts.synthesize(line, SpeechStyle(players.getValue(name)))
            // כל הדמיונות, לא רק המנצח — כדי לראות כמה הקולות רחוקים זה מזה.
            val vector = recognizer.voiceprint(wav)!!
            println("=== $name: " + enrolled.entries.joinToString { (who, known) -> "$who %.2f".format(similarity(vector, known)) })
            println("    ניחוש: ${voices.identify(wav)}")
        }
    }
}
