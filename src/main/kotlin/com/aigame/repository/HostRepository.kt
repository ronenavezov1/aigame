package com.aigame.repository

import com.aigame.objects.ConversationLine
import com.aigame.objects.HostEvent
import com.aigame.objects.Player
import com.aigame.orchestrator.HostInput
import com.aigame.orchestrator.HostOutput
import com.aigame.state.AppStateManager
import com.aigame.state.Expression
import com.aigame.state.Phase
import com.aigame.state.Utterance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import kotlin.random.Random

private val log = LoggerFactory.getLogger("HostRepository")

// כמה זמן המארח נשאר ער אחרי שסיים, לפני שהוא חוזר לישון.
private const val STAY_AWAKE_MS = 6_000L

// כמה פעמים מתוך מאה ההגרלה בוחרת זוג במקום שחקן אחד.
private const val PAIR_PERCENT = 30

private const val MISHEARD = "מה? לא הבנתי."
private const val NO_EARS = "שמעתי משהו, אבל אין לי עדיין אוזניים לעברית. F1 כדי להקליד."

// ============================================================
//  המארח: לוקח אירוע, מפעיל את המוח, ומיישם את מה שחזר על המצב.
//  על כל תשובה של המוח — שתי שורות בשיחה: הקלט, והתשובה.
//
//      hear / wakeNow     הקלט — נכנס ל-hostEventFlow
//      speechFinished     המסך סיים להקריא
//
//  את הקול, הכתובית והפרצוף מציג המסך, לפי HostState.
// ============================================================

class HostRepository(
    private val stateManager: AppStateManager,
    private val agent: AgentRepository,
    private val memory: MemoryRepository,
    private val voice: VoiceRepository,
    private val fillers: FillerRepository,
    private val scope: CoroutineScope,
    private val random: Random = Random.Default,
) {

    fun start() {
        // UNDISPATCHED: נרשם ל-hostEventFlow כבר עכשיו, כדי שאירוע ראשון לא יפוספס.
        scope.launch(start = CoroutineStart.UNDISPATCHED) { handleEvents() }
        scope.launch { wakeAndSleep() }
    }

    /** טקסט מהפאנל — עובר בדיוק כמו משפט שנאמר במיקרופון. */
    fun hear(text: String) {
        val clean = text.trim()
        if (clean.isNotEmpty()) stateManager.addHostEvent(HostEvent.Heard(clean))
    }

    fun wakeNow() = stateManager.addHostEvent(HostEvent.WakeUp)

    /** המסך סיים להקריא — אפשר לעבור לאירוע הבא. */
    fun speechFinished() {
        stateManager.updateHost { if (it.phase == Phase.SPEAKING) it.copy(phase = Phase.AWAKE, utterance = null) else it }
    }

    // ============================================================
    //  אירועים — אחד אחרי השני. אירוע שנכשל לא עוצר את הבאים.
    // ============================================================

    private suspend fun handleEvents() {
        stateManager.hostEventFlow.collect { event ->
            try {
                handle(event)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("טיפול באירוע נכשל: {}", event, e)
                stateManager.updateHost { it.copy(phase = Phase.AWAKE) }
            }
        }
    }

    private suspend fun handle(event: HostEvent) {
        when (event) {
            is HostEvent.Heard -> {
                // מי דיבר: מה שזיהוי הקול חושב, ואם הוא לא יודע — user. הסוכן יכול לתקן.
                val who = event.speaker?.name ?: ConversationLine.USER
                respond(HostInput.Heard(event.text, event.speaker), ConversationLine(who, event.text))
            }

            HostEvent.WakeUp -> {
                // בלי שחקנים שהגיעו אין למי לתת משימה. ממשיכים לישון.
                if (stateManager.hostMemoryState.value.present.isEmpty()) {
                    log.info("התעוררות דולגה — אין שחקנים נוכחים")
                    return
                }
                memory.countWakeUp()
                val chosen = pickPlayers()
                val line = ConversationLine(ConversationLine.AGENT, "קמתי מעצמי. ההגרלה בחרה: ${chosen.joinToString { it.name }}")
                respond(HostInput.WakeUp(chosen), line)
            }

            HostEvent.Misheard -> {
                val hasEars = stateManager.environmentState.value.isSpeechRecognitionConfigured
                if (!hasEars) return say(HostOutput(NO_EARS, Expression.SUSPICIOUS))
                // מוקלט מראש — יוצא מיד. עוד לא הוקלט: בהקראה רגילה.
                val line = fillers.misheard()
                if (line != null) say(HostOutput(line.text, Expression.SUSPICIOUS), recorded = line.audio)
                else say(HostOutput(MISHEARD, Expression.SUSPICIOUS))
            }
        }
    }

    /** מפעיל את המוח, רושם בשיחה את הקלט ואת התשובה, ואומר אותה. */
    private suspend fun respond(input: HostInput, inputLine: ConversationLine) {
        // הודעה חדשה: הסוכן רואה רק את רבע השעה האחרונה של השיחה.
        memory.forgetOldConversation()
        val output = coroutineScope {
            stateManager.updateHost { it.copy(phase = Phase.THINKING, subtitle = null) }
            val thinking = async { agent.think(input) }
            // אורח מחכה: בזמן שהמוח חושב — משפט המתנה מוקלט מראש, במקום שקט.
            if (input is HostInput.Heard) fillers.pick()?.let { filler ->
                say(HostOutput(filler.text), recorded = filler.audio)
                stateManager.updateHost { it.copy(phase = Phase.THINKING, subtitle = null) }
            }
            thinking.await()
        }

        val line = output.speaker?.let { inputLine.copy(who = it) } ?: inputLine
        memory.addConversation(line, ConversationLine(ConversationLine.AGENT, output.speech))

        // דיבר שחקן מזוהה: מנתח השחקנים מעדכן את הכרטיס שלו — ברקע, בלי לעכב את התשובה.
        if (stateManager.hostMemoryState.value.player(line.who) != null) scope.launch { agent.profile(line.who) }
        say(output)
    }

    /**
     * ממיר את המשפט לאודיו, מעביר את המארח למצב "מדבר", ומחכה שהמסך יסיים
     * להקריא (speechFinished). בלי מסך — למשל בבדיקות — ממשיכים אחרי זמן סביר.
     */
    private suspend fun say(output: HostOutput, recorded: ByteArray? = null) {
        // ההקראה לוקחת כמה שניות — בינתיים הוא "חושב", לא נראה קפוא ולא נרדם.
        if (recorded == null) stateManager.updateHost { if (it.phase == Phase.SPEAKING) it else it.copy(phase = Phase.THINKING) }
        val utterance = Utterance(output.speech, recorded ?: voice.synthesize(output.speech, output.style))
        stateManager.updateHost {
            it.copy(phase = Phase.SPEAKING, expression = output.expression, subtitle = output.speech, utterance = utterance)
        }
        withTimeoutOrNull(5_000L + output.speech.length * 150L) {
            stateManager.hostState.first { it.phase != Phase.SPEAKING }
        }
        speechFinished()
    }

    // ============================================================
    //  שחקנים
    // ============================================================

    /** מבין מי שהגיע: מי שנבחר הכי מעט קודם; בין שווים — אקראי. לפעמים זוג. */
    private fun pickPlayers(): List<Player> {
        val players = stateManager.hostMemoryState.value.present
        val count = if (players.size >= 2 && random.nextInt(100) < PAIR_PERCENT) 2 else 1
        val chosen = players.shuffled(random).sortedBy { it.picks }.take(count)
        memory.markPicked(chosen.map { it.name })
        return chosen
    }

    // ============================================================
    //  ער / ישן
    //
    //  לוחצים על הכפתור → מתעורר. ער ולא מקשיבים לו → אחרי כמה שניות
    //  חוזר לישון. collectLatest מבטל את ההמתנה ברגע שמשהו משתנה.
    // ============================================================

    private suspend fun wakeAndSleep() {
        combine(stateManager.hostState, stateManager.microphoneState) { host, mic ->
            Triple(host.phase, mic.isListening, mic.isUnderstanding)
        }
            .distinctUntilChanged()
            .collectLatest { (phase, isListening, isUnderstanding) ->
                when {
                    isListening && phase == Phase.SLEEPING ->
                        stateManager.updateHost { it.copy(phase = Phase.AWAKE) }

                    // הקלטה שעוד הופכת לטקסט — עוד מדברים איתו, אז לא נרדמים.
                    !isListening && !isUnderstanding && phase == Phase.AWAKE -> {
                        delay(STAY_AWAKE_MS)
                        stateManager.updateHost {
                            if (it.phase == Phase.AWAKE) it.copy(phase = Phase.SLEEPING, expression = Expression.NEUTRAL, subtitle = null)
                            else it
                        }
                    }
                }
            }
    }
}
