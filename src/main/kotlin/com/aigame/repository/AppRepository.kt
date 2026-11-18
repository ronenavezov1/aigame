package com.aigame.repository

import com.aigame.Env
import com.aigame.HOST_NAME
import com.aigame.speech.GeminiApi
import com.aigame.speech.GeminiSpeechToText
import com.aigame.speech.GeminiTextToSpeech
import com.aigame.speech.NoSpeakerRecognizer
import com.aigame.speech.NoSpeechToText
import com.aigame.speech.NoTextToSpeech
import com.aigame.speech.SherpaSpeakerRecognizer
import com.aigame.speech.SpeakerRecognizer
import com.aigame.state.AppStateManager
import com.aigame.storage.MemoryStorage
import com.aigame.storage.VoiceprintStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

private val log = LoggerFactory.getLogger("AppRepository")

// ============================================================
//  שורש ההרכבה. בונה את AppStateManager, טוען את הזיכרון מהקבצים,
//  ובונה את שירותי הדיבור ואת הרפוזיטורים. המיקרופון והרמקול הם של
//  המסך — Main בונה אותם.
//
//      AppRepository
//        ├── appStateManager   המצב — מקור האמת היחיד
//        ├── microphone        MicrophoneRepository — הקלטה → מי אמר ומה → למארח
//        ├── voiceprints       VoiceprintRepository — איתחול טביעות קול
//        ├── memory            MemoryRepository — הזיכרון; מהמסך: מי נוכח
//        ├── host              HostRepository — המארח (Agent + Memory + Voice)
//        └── (ברקע)            WakeUpRepository
// ============================================================

class AppRepository private constructor(
    val appStateManager: AppStateManager,
    val microphone: MicrophoneRepository,
    val voiceprints: VoiceprintRepository,
    val memory: MemoryRepository,
    val host: HostRepository,
    private val scope: CoroutineScope,
) : AutoCloseable {

    /** עוצר את המאזינים. אין מה לשמור — כל שינוי בזיכרון כבר נכתב לקובץ. */
    override fun close() = scope.cancel()

    companion object {
        fun create(): AppRepository {
            // SupervisorJob: מאזין אחד שנכשל לא מפיל את השאר.
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

            val dataDir = Path.of(Env.get("PARTY_DATA_DIR", "data")).toAbsolutePath()
            Files.createDirectories(dataDir)
            log.info("הזיכרון נשמר ב-{}", dataDir)

            val appStateManager = AppStateManager()
            val memory = MemoryRepository(appStateManager, MemoryStorage(dataDir)).also { it.load() }

            // שירותי הדיבור. מפתח אחד של Gemini = קול ושמיעה. בלעדיו: כתובית והקלדה.
            val gemini = Env.get("GOOGLE_API_KEY")?.let(::GeminiApi)
            if (gemini == null) log.warn("אין GOOGLE_API_KEY — בלי קול ובלי שמיעה")
            val stt = gemini?.let { GeminiSpeechToText(it, model = Env.get("GOOGLE_STT_MODEL", "gemini-3.8-flash")) }
                ?: NoSpeechToText
            val tts = gemini?.let(::GeminiTextToSpeech) ?: NoTextToSpeech

            // טביעת קול: מודל מקומי. בלעדיו — כל מי שמדבר הוא "user".
            val recognizer = speakerRecognizer(Path.of(Env.get("VOICEPRINT_MODEL", "models/wespeaker_en_voxceleb_resnet34_LM.onnx")))
            val voiceprints = VoiceprintRepository(appStateManager, recognizer, VoiceprintStorage(dataDir)).also { it.load() }

            val agent = AgentRepository(appStateManager, memory)
            appStateManager.updateEnvironment {
                it.copy(
                    isBrainConfigured = agent.isConfigured,
                    isSpeechRecognitionConfigured = stt.isConfigured,
                    isVoiceprintConfigured = recognizer.isConfigured,
                )
            }

            val voice = VoiceRepository(tts)
            // משפטי ההמתנה מוקלטים ברקע בהדלקה הראשונה, ונשמרים ב-data/fillers.
            val fillers = FillerRepository(tts, dataDir.resolve("fillers")).also { scope.launch { it.prepare() } }
            val host = HostRepository(appStateManager, agent, memory, voice, fillers, scope).also { it.start() }
            val microphone = MicrophoneRepository(appStateManager, stt, voiceprints, scope, dataDir)

            val minMinutes = Env.get("WAKE_MIN_MINUTES")?.toLongOrNull() ?: 5
            val maxMinutes = (Env.get("WAKE_MAX_MINUTES")?.toLongOrNull() ?: 10).coerceAtLeast(minMinutes)
            val schedule = WakeSchedule(minMinutes * 60_000, maxMinutes * 60_000)
            WakeUpRepository(appStateManager, memory, schedule, scope).start()
            log.info("{} מוכן. מתעורר לבד אחרי {}–{} דקות של שקט", HOST_NAME, minMinutes, maxMinutes)

            return AppRepository(appStateManager, microphone, voiceprints, memory, host, scope)
        }

        /** יש מודל והספרייה המקומית נטענת — טביעת קול. אחרת בלעדיה, והמסיבה ממשיכה. */
        private fun speakerRecognizer(model: Path): SpeakerRecognizer {
            if (!Files.exists(model)) {
                log.warn("אין מודל טביעת קול ב-{} — כל מי שמדבר הוא user", model.toAbsolutePath())
                return NoSpeakerRecognizer
            }
            return try {
                SherpaSpeakerRecognizer(model)
            } catch (e: UnsatisfiedLinkError) {
                log.error("הספרייה של טביעת הקול לא נטענה: {}", e.message)
                NoSpeakerRecognizer
            } catch (e: Exception) {
                log.error("טביעת הקול לא עלתה: {}", e.message)
                NoSpeakerRecognizer
            }
        }
    }
}
