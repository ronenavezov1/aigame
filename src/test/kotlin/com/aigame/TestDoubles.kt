package com.aigame

import com.aiagent.lib.agent.AgentCapability
import com.aiagent.lib.agent.AgentHistory
import com.aiagent.lib.llm.LlmClient
import com.aiagent.lib.llm.LlmResponse
import com.aiagent.lib.llm.ModelTier
import com.aigame.objects.Ability
import com.aigame.objects.HostExamples
import com.aigame.objects.Player
import com.aigame.objects.Task
import com.aigame.orchestrator.HostOrchestratorContext
import com.aigame.orchestrator.Llms
import com.aigame.repository.AgentRepository
import com.aigame.repository.FillerRepository
import com.aigame.repository.HostRepository
import com.aigame.repository.MemoryRepository
import com.aigame.repository.VoiceRepository
import com.aigame.speech.NoTextToSpeech
import com.aigame.speech.SpeakerRecognizer
import com.aigame.state.AppStateManager
import com.aigame.state.Expression
import com.aigame.state.HostMemoryState
import com.aigame.state.Phase
import com.aigame.storage.MemoryStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** מודל מזויף: עונה לפי תסריט, וזוכר מה קיבל. */
class ScriptedLlm(private val script: (List<AgentHistory>) -> LlmResponse) : LlmClient {
    val seen = mutableListOf<List<AgentHistory>>()
    val systemPrompts = mutableListOf<String>()
    override val providerName = "fake"
    override fun modelNameFor(tier: ModelTier) = "fake"
    override suspend fun sendMessage(
        agentId: String,
        systemPrompt: String,
        history: List<AgentHistory>,
        availableCapabilities: List<AgentCapability>,
        tier: ModelTier,
    ): LlmResponse {
        seen += history
        systemPrompts += systemPrompt
        return script(history)
    }
}

/** טביעת קול מזויפת: הבית הראשון בהקלטה אומר של מי הקול. */
class FakeRecognizer(private val voices: Map<Byte, FloatArray>) : SpeakerRecognizer {
    override val isConfigured = true
    override fun voiceprint(wav: ByteArray): FloatArray? = voices[wav.first()]
}

/** הקלטה מזויפת של [who], באורך [seconds] שניות (16kHz, 16 ביט). */
fun recording(who: Int, seconds: Int = 5) = ByteArray(seconds * 32_000) { who.toByte() }

/**
 * WAV אמיתי של צליל עם צלילים עיליים — כמו קול, בגובה [hz]. מתחיל ב-'R' (RIFF).
 * volume 0 = שקט. volume 4 = נחתך בתקרה, כמו מיקרופון מוגבר מדי.
 */
fun voiceLike(hz: Double, seconds: Double = 2.0, rate: Int = 16_000, volume: Double = 1.0): ByteArray {
    val n = (seconds * rate).toInt()
    val pcm = ByteArray(n * 2)
    for (i in 0 until n) {
        val t = i.toDouble() / rate
        val v = 0.5 * sin(2 * PI * hz * t) + 0.25 * sin(4 * PI * hz * t) + 0.12 * sin(6 * PI * hz * t)
        val sample = (v * 20_000 * volume).toInt().coerceIn(-32768, 32767)
        pcm[2 * i] = (sample and 0xFF).toByte()
        pcm[2 * i + 1] = (sample shr 8).toByte()
    }
    val format = AudioFormat(rate.toFloat(), 16, 1, true, false)
    val wav = ByteArrayOutputStream()
    AudioSystem.write(AudioInputStream(ByteArrayInputStream(pcm), format, n.toLong()), AudioFileFormat.Type.WAVE, wav)
    return wav.toByteArray()
}

/** context בלי קבצים ובלי state: מה שהסוכן רושם נשאר כאן, כדי לבדוק אותו. */
class FakeContext(
    override val llms: Llms,
    override var memory: HostMemoryState = HostMemoryState(),
    override val examples: HostExamples = HostExamples(),
) : HostOrchestratorContext {
    override fun addPlayer(player: Player) {
        memory = memory.copy(players = memory.players + (player.name to player))
    }

    override fun grantAbility(player: String, ability: Ability) {
        memory = memory.copy(players = memory.players.mapValues { (name, p) ->
            if (name == player) p.copy(abilities = p.abilities + ability.hebrew) else p
        })
    }

    override fun useAbility(player: String, ability: Ability): Boolean {
        val has = memory.players[player]?.abilities?.contains(ability.hebrew) == true
        if (has) memory = memory.copy(players = memory.players.mapValues { (name, p) ->
            if (name == player) p.copy(abilities = p.abilities - ability.hebrew) else p
        })
        return has
    }

    override fun addTask(task: Task) {
        memory = memory.copy(players = memory.players.mapValues { (name, player) ->
            if (name in task.players) player.copy(tasks = player.tasks + task.text) else player
        })
    }
}

/**
 * המארח עם אותו חיווט כמו ב-AppRepository: מצב, זיכרון על הדיסק, מוח מזויף.
 * במקום מסך — מאזין שמקריא כל מה שהמארח אומר, ומדווח שסיים.
 *
 * הסקופ הוא לא backgroundScope: advanceUntilIdle לא מחכה לעבודה שרצה שם,
 * והמאזינים של הרפוזיטורים הם בדיוק עבודה כזו.
 */
class TestParty(
    test: TestScope,
    llm: LlmClient?,
    dir: Path,
    fillers: FillerRepository = FillerRepository(NoTextToSpeech, dir.resolve("fillers")),
) {
    val scope = CoroutineScope(StandardTestDispatcher(test.testScheduler) + SupervisorJob())
    val appStateManager = AppStateManager()
    val host: HostRepository

    /** מה שה"מסך" הקריא: טקסט והבעה. */
    val spoken = mutableListOf<Pair<String, Expression>>()

    /** הזיכרון כרגע, ב-state. */
    val memory: HostMemoryState get() = appStateManager.hostMemoryState.value

    init {
        val memoryRepository = MemoryRepository(appStateManager, MemoryStorage(dir)).also { it.load() }
        val agent = AgentRepository(appStateManager, memoryRepository, Llms(claude = llm))
        appStateManager.updateEnvironment { it.copy(isBrainConfigured = agent.isConfigured) }
        val voice = VoiceRepository(NoTextToSpeech)
        host = HostRepository(appStateManager, agent, memoryRepository, voice, fillers, scope, Random(7)).also { it.start() }

        scope.launch {
            appStateManager.hostState.collect { state ->
                if (state.phase == Phase.SPEAKING) {
                    spoken += state.subtitle.orEmpty() to state.expression
                    host.speechFinished()
                }
            }
        }
    }

    fun close() = scope.cancel()
}
