package com.aigame

import com.aiagent.lib.agent.AgentHistory
import com.aiagent.lib.llm.FunctionCall
import com.aiagent.lib.llm.LlmResponse
import com.aigame.objects.Address
import com.aigame.objects.ConversationLine
import com.aigame.objects.HostEvent
import com.aigame.objects.Player
import com.aigame.objects.SpeakerGuess
import com.aigame.repository.FillerRepository
import com.aigame.repository.MemoryRepository
import com.aigame.repository.WakeSchedule
import com.aigame.repository.WakeUpRepository
import com.aigame.speech.SpeechStyle
import com.aigame.speech.TextToSpeech
import com.aigame.state.Expression
import com.aigame.state.Phase
import com.aigame.storage.MemoryStorage
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HostRepositoryTest {

    private val dir = Files.createTempDirectory("host-test")
    private val parties = mutableListOf<TestParty>()

    @AfterTest
    fun close() = parties.forEach { it.close() }

    private fun TestScope.party(llm: ScriptedLlm?) = TestParty(this, llm, dir).also { parties += it }

    /** "הפעלה מחדש": מה שנטען מהקבצים, בלי ה-state. */
    private fun restart() = MemoryStorage(dir).load()

    @Test
    fun `after a quiet minute he wakes up by himself, falls asleep, and wakes up again`() = runTest {
        val llm = ScriptedLlm { LlmResponse(textReply = "קמתי. משימה!") }
        val party = party(llm)
        party.appStateManager.updateHostMemory { it.copy(players = mapOf("רוני" to Player("רוני"))) }
        val memory = MemoryRepository(party.appStateManager, MemoryStorage(dir))
        // backgroundScope: השעון לא נגמר אף פעם — הוא מתבטל לבד כשהבדיקה נגמרת.
        WakeUpRepository(party.appStateManager, memory, WakeSchedule(60_000, 60_000), backgroundScope).start()

        advanceTimeBy(61_000)
        assertEquals(1, party.spoken.size, "אחרי דקה של שקט הוא היה צריך לקום")
        assertTrue("קמת מעצמך" in (llm.seen.first().first() as AgentHistory.UserInput).text)

        // מדבר, נרדם אחרי כמה שניות, והטיימר מתחיל מחדש.
        advanceTimeBy(75_000)
        assertEquals(2, party.spoken.size, "ואחרי עוד דקה של שקט — שוב")
        assertEquals(2, party.memory.clock.wakeCount)
    }

    @Test
    fun `wake-ups pick only players who came`() = runTest {
        val party = party(ScriptedLlm { LlmResponse(textReply = "משימה!") })
        party.appStateManager.updateHostMemory {
            it.copy(players = mapOf("רוני" to Player("רוני"), "אורן" to Player("אורן", present = false)))
        }

        repeat(3) { party.host.wakeNow() }
        advanceUntilIdle()

        assertEquals(3, party.memory.players.getValue("רוני").picks)
        assertEquals(0, party.memory.players.getValue("אורן").picks)
    }

    @Test
    fun `nobody came - he does not wake up`() = runTest {
        val llm = ScriptedLlm { error("לא אמור להיקרא") }
        val party = party(llm)
        party.appStateManager.updateHostMemory { it.copy(players = mapOf("אורן" to Player("אורן", present = false))) }

        party.host.wakeNow()
        advanceUntilIdle()

        assertTrue(llm.seen.isEmpty())
    }

    @Test
    fun `while a recording is still being understood - he stays awake`() = runTest {
        val party = party(ScriptedLlm { LlmResponse(textReply = "סבבה.") })
        party.appStateManager.updateMicrophone { it.copy(isListening = true) }
        advanceTimeBy(100)

        // שחררו את הכפתור, והתמלול לוקח הרבה זמן.
        party.appStateManager.updateMicrophone { it.copy(isListening = false, isUnderstanding = true) }
        advanceTimeBy(30_000)
        assertEquals(Phase.AWAKE, party.appStateManager.hostState.value.phase)

        party.appStateManager.updateMicrophone { it.copy(isUnderstanding = false) }
        advanceTimeBy(7_000)
        assertEquals(Phase.SLEEPING, party.appStateManager.hostState.value.phase)
    }

    @Test
    fun `misheard - a recorded line comes out right away, without the model`() = runTest {
        val tts = object : TextToSpeech {
            override suspend fun synthesize(text: String, style: SpeechStyle) = text.toByteArray()
        }
        val fillers = FillerRepository(tts, dir.resolve("fillers")).also { it.prepare() }
        val llm = ScriptedLlm { error("לא אמור להיקרא") }
        val party = TestParty(this, llm, dir, fillers).also { parties += it }

        party.appStateManager.addHostEvent(HostEvent.Misheard)
        advanceUntilIdle()

        assertEquals(Expression.SUSPICIOUS, party.spoken.single().second)
        assertTrue(llm.seen.isEmpty())
    }

    @Test
    fun `while the brain thinks - a recorded filler line, then the answer`() = runTest {
        val tts = object : TextToSpeech {
            override suspend fun synthesize(text: String, style: SpeechStyle) = text.toByteArray()
        }
        val fillers = FillerRepository(tts, dir.resolve("fillers")).also { it.prepare() }
        val party = TestParty(this, ScriptedLlm { LlmResponse(textReply = "תשובה.") }, dir, fillers).also { parties += it }

        party.host.hear("היי")
        advanceUntilIdle()

        assertEquals(2, party.spoken.size)
        assertEquals("תשובה.", party.spoken.last().first)
        // משפט ההמתנה לא נכנס לשיחה — הוא לא תשובה של הסוכן.
        assertEquals(listOf("היי", "תשובה."), party.memory.conversation.map { it.text })
    }

    @Test
    fun `after an identified player talks - the profiler updates his card, in memory and on disk`() = runTest {
        val profilerInputs = mutableListOf<String>()
        val llm = ScriptedLlm { history ->
            val input = (history.first() as AgentHistory.UserInput).text
            when {
                history.last() !is AgentHistory.UserInput -> LlmResponse(textReply = "סבבה.")
                input.startsWith("השחקן:") -> {
                    profilerInputs += input
                    LlmResponse(functionCalls = listOf(FunctionCall("update_profile", mapOf("background" to "מהנדס, אוהב בירה", "character" to "מתחכם"))))
                }
                else -> LlmResponse(textReply = "תרגע, מהנדס.")
            }
        }
        val party = party(llm)
        party.appStateManager.updateHostMemory { it.copy(players = mapOf("רוני" to Player("רוני"))) }

        party.appStateManager.addHostEvent(HostEvent.Heard("אני מהנדס, אני אוהב בירה, ואתה סתם תוכנה", SpeakerGuess("רוני", 0.8f)))
        advanceUntilIdle()

        // הוא קיבל את ההודעה שעליו לבדוק שהיא באמת של רוני.
        assertTrue("ההודעה האחרונה, שזיהוי הקול שייך לו: «אני מהנדס" in profilerInputs.single(), profilerInputs.single())
        assertEquals("מתחכם", party.memory.players.getValue("רוני").character)
        assertEquals("מהנדס, אוהב בירה", restart().players.getValue("רוני").background)
    }

    @Test
    fun `an unidentified guest does not wake the profiler`() = runTest {
        val llm = ScriptedLlm { history ->
            val input = (history.first() as AgentHistory.UserInput).text
            check(!input.startsWith("השחקן:")) { "המנתח לא אמור לרוץ על אורח שלא זוהה" }
            LlmResponse(textReply = "סבבה.")
        }
        val party = party(llm)
        party.appStateManager.updateHostMemory { it.copy(players = mapOf("רוני" to Player("רוני"))) }

        party.host.hear("מישהו ראה את המפתחות?")
        advanceUntilIdle()

        assertEquals(1, llm.seen.size)
    }

    @Test
    fun `a prize stays with the player until he uses it - then it is gone`() = runTest {
        val llm = ScriptedLlm { history ->
            val said = (history.firstOrNull() as? AgentHistory.UserInput)?.text.orEmpty().substringAfter("עכשיו:")
            when {
                history.last() !is AgentHistory.UserInput -> LlmResponse(textReply = "נרשם.")
                "פרס" in said -> LlmResponse(functionCalls = listOf(FunctionCall("grant_ability", mapOf("player" to "רוני", "ability" to "חנינה"))))
                else -> LlmResponse(functionCalls = listOf(FunctionCall("use_ability", mapOf("player" to "רוני", "ability" to "חנינה"))))
            }
        }
        val party = party(llm)
        party.appStateManager.updateHostMemory { it.copy(players = mapOf("רוני" to Player("רוני"))) }

        party.host.hear("תן לרוני פרס")
        advanceUntilIdle()
        assertEquals(listOf("חנינה"), restart().players.getValue("רוני").abilities)

        party.host.hear("אני משתמש בחנינה שלי")
        advanceUntilIdle()
        assertEquals(emptyList(), restart().players.getValue("רוני").abilities)
    }

    @Test
    fun `who spoke - the voice guess, unless the agent knows better`() = runTest {
        val llm = ScriptedLlm { history ->
            // רק מה שנאמר עכשיו — הקלט כולל גם את השיחה של רבע השעה האחרונה.
            val said = (history.firstOrNull() as? AgentHistory.UserInput)?.text.orEmpty().substringAfter("עכשיו:")
            when {
                history.last() !is AgentHistory.UserInput -> LlmResponse(textReply = "סבבה.")
                "זאת דנה" in said -> LlmResponse(functionCalls = listOf(FunctionCall("set_speaker", mapOf("name" to "דנה"))))
                else -> LlmResponse(textReply = "סבבה.")
            }
        }
        val party = party(llm)
        party.appStateManager.updateHostMemory { it.copy(players = listOf("רוני", "דנה").associateWith(::Player)) }

        party.appStateManager.addHostEvent(HostEvent.Heard("עוד בירה", SpeakerGuess("רוני", 0.8f)))
        party.appStateManager.addHostEvent(HostEvent.Heard("זאת דנה, לא רוני", SpeakerGuess("רוני", 0.55f)))
        party.appStateManager.addHostEvent(HostEvent.Heard("מישהו ראה את המפתחות?"))
        advanceUntilIdle()

        val guests = party.memory.conversation.filter { it.who != ConversationLine.AGENT }.map { it.who }
        assertEquals(listOf("רוני", "דנה", ConversationLine.USER), guests)
    }

    @Test
    fun `guest introduces herself - registered, answered, and both are on disk`() = runTest {
        val llm = ScriptedLlm { history ->
            if (history.last() is AgentHistory.UserInput) {
                LlmResponse(
                    functionCalls = listOf(
                        FunctionCall("add_player", mapOf("name" to "דנה", "background" to "חוגגת יום הולדת", "address" to "נקבה")),
                        FunctionCall("set_face", mapOf("expression" to "HAPPY")),
                    ),
                )
            } else {
                LlmResponse(textReply = "ברוכה הבאה, דנה!")
            }
        }
        val party = party(llm)

        party.host.hear("היי, אני דנה, ואני חוגגת היום יום הולדת")
        advanceUntilIdle()

        assertEquals(listOf("ברוכה הבאה, דנה!" to Expression.HAPPY), party.spoken)
        val dana = party.memory.players.getValue("דנה")
        assertEquals(Address.FEMALE, dana.address)
        assertEquals("חוגגת יום הולדת", dana.background)
        assertEquals(
            listOf(
                ConversationLine.USER to "היי, אני דנה, ואני חוגגת היום יום הולדת",
                ConversationLine.AGENT to "ברוכה הבאה, דנה!",
            ),
            party.memory.conversation.map { it.who to it.text },
        )

        // הכל כבר בקבצים — בלי לחכות לשום שמירה.
        assertEquals(party.memory.players, restart().players)

        // אחרי השקט הוא חוזר לישון, וההבעה מתאפסת.
        assertEquals(Phase.SLEEPING, party.appStateManager.hostState.value.phase)
        assertEquals(Expression.NEUTRAL, party.appStateManager.hostState.value.expression)
    }

    @Test
    fun `what he learns about a player during the game is kept - nothing known is lost`() = runTest {
        val llm = ScriptedLlm { history ->
            if (history.last() is AgentHistory.UserInput) {
                LlmResponse(functionCalls = listOf(FunctionCall("add_player", mapOf("name" to "דנה", "character" to "תחרותית, לא מוותרת"))))
            } else {
                LlmResponse(textReply = "רשמתי.")
            }
        }
        val party = party(llm)
        party.appStateManager.updateHostMemory {
            it.copy(players = mapOf("דנה" to Player("דנה", background = "חוגגת 30", address = Address.FEMALE)))
        }

        party.host.hear("דנה ניצחה שוב בבירפונג ולא מפסיקה להתרברב")
        advanceUntilIdle()

        val dana = restart().players.getValue("דנה")
        assertEquals("תחרותית, לא מוותרת", dana.character)
        assertEquals("חוגגת 30", dana.background)
        assertEquals(Address.FEMALE, dana.address)
    }

    @Test
    fun `a task reaches every player in it, and the players file`() = runTest {
        val llm = ScriptedLlm { history ->
            if (history.last() is AgentHistory.UserInput) {
                LlmResponse(functionCalls = listOf(FunctionCall("create_task", mapOf("players" to "רוני, דנה", "task" to "לשיר ביחד"))))
            } else {
                LlmResponse(textReply = "רוני ודנה — לשיר!")
            }
        }
        val party = party(llm)
        party.appStateManager.updateHostMemory { it.copy(players = listOf("רוני", "דנה").associateWith(::Player)) }

        party.host.hear("תן לנו משימה")
        advanceUntilIdle()

        assertEquals(listOf("לשיר ביחד"), party.memory.players.getValue("רוני").tasks)
        assertEquals(listOf("לשיר ביחד"), party.memory.players.getValue("דנה").tasks)

        assertEquals(listOf("לשיר ביחד"), restart().players.getValue("דנה").tasks)
    }

    @Test
    fun `wake-ups are fair - no one gets a second task before everyone got one`() = runTest {
        val party = party(ScriptedLlm { LlmResponse(textReply = "משימה!") })
        party.appStateManager.updateHostMemory { it.copy(players = listOf("א", "ב", "ג").associateWith(::Player)) }

        repeat(3) { party.host.wakeNow() }
        advanceUntilIdle()

        val picks = party.memory.players.values.map { it.picks }
        assertTrue(picks.min() >= 1, "כולם נבחרו: $picks")
        assertTrue(picks.max() - picks.min() <= 1, "אף אחד לא נבחר פעמיים לפני אחר: $picks")
        assertEquals(3, party.memory.clock.wakeCount)
        assertEquals(3, party.spoken.size)
        // בהתעוררות גם הקלט הוא של הסוכן.
        assertEquals(6, party.memory.conversation.size)
        assertTrue(party.memory.conversation.all { it.who == ConversationLine.AGENT })
        assertEquals(3, restart().clock.wakeCount)
    }

    @Test
    fun `wake-up without players does not call the model`() = runTest {
        val llm = ScriptedLlm { error("לא אמור להיקרא") }
        val party = party(llm)

        party.host.wakeNow()
        advanceUntilIdle()

        assertTrue(llm.seen.isEmpty())
        assertTrue(party.spoken.isEmpty())
    }

    @Test
    fun `no API key - the host still says something, and the turn is remembered`() = runTest {
        val party = party(llm = null)

        party.host.hear("שלום")
        advanceUntilIdle()

        assertTrue(party.spoken.single().first.contains("אין לי מוח"))
        assertEquals(listOf(ConversationLine.USER, ConversationLine.AGENT), party.memory.conversation.map { it.who })
    }

    @Test
    fun `misheard - the host says he did not understand, without the model`() = runTest {
        val llm = ScriptedLlm { error("לא אמור להיקרא") }
        val party = party(llm)

        party.appStateManager.addHostEvent(HostEvent.Misheard)
        advanceUntilIdle()

        assertEquals(listOf("מה? לא הבנתי." to Expression.SUSPICIOUS), party.spoken)
        assertTrue(llm.seen.isEmpty())
    }
}
