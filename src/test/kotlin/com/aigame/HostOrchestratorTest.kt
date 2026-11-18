package com.aigame

import com.aiagent.lib.agent.AgentHistory
import com.aiagent.lib.llm.FunctionCall
import com.aiagent.lib.llm.LlmResponse
import com.aigame.objects.Address
import com.aigame.objects.ConversationLine
import com.aigame.objects.HostExamples
import com.aigame.objects.Player
import com.aigame.objects.SpeakerGuess
import com.aigame.objects.StyleExamples
import com.aigame.objects.TaskExample
import com.aigame.orchestrator.HostInput
import com.aigame.orchestrator.HostOrchestrator
import com.aigame.orchestrator.Llms
import com.aigame.state.Expression
import com.aigame.state.HostMemoryState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** הסוכן לבד, בארגז החול שלו: כל מה שיש לו הוא ה-context. */
class HostOrchestratorTest {

    @Test
    fun `output carries the speech and the face - players and tasks go through the context`() = runTest {
        val llm = ScriptedLlm { history ->
            if (history.last() is AgentHistory.UserInput) {
                LlmResponse(
                    functionCalls = listOf(
                        FunctionCall("add_player", mapOf("name" to "רוני", "background" to "כתב את הסוכן", "address" to "זכר")),
                        FunctionCall("create_task", mapOf("players" to "רוני", "task" to "לנאום")),
                        FunctionCall("set_face", mapOf("expression" to "LAUGHING")),
                    ),
                )
            } else {
                LlmResponse(textReply = "  רוני, נאום!  ")
            }
        }
        val context = FakeContext(Llms(claude = llm))

        val output = HostOrchestrator(context).run(HostInput.Heard("אני רוני, כתבתי אותך"))

        assertEquals("רוני, נאום!", output.speech)
        assertEquals(Expression.LAUGHING, output.expression)
        assertEquals(
            Player("רוני", background = "כתב את הסוכן", address = Address.MALE, tasks = listOf("לנאום")),
            context.memory.players.getValue("רוני"),
        )
    }

    @Test
    fun `a task for someone who is not registered is refused, so the agent can fix it`() = runTest {
        val llm = ScriptedLlm { history ->
            if (history.last() is AgentHistory.UserInput) {
                LlmResponse(functionCalls = listOf(FunctionCall("create_task", mapOf("players" to "מישהו", "task" to "לרקוד"))))
            } else {
                LlmResponse(textReply = "אופס")
            }
        }
        val context = FakeContext(Llms(claude = llm))

        HostOrchestrator(context).run(HostInput.Heard("תן משימה למישהו"))

        assertTrue(context.memory.players.isEmpty())
        val result = llm.seen.last().filterIsInstance<AgentHistory.ToolExecutionResult>().single().resultData
        assertTrue("אין שחקנים בשם" in result, result)
    }

    @Test
    fun `the agent sees the whole memory, and a wake-up names the chosen players`() = runTest {
        val llm = ScriptedLlm { LlmResponse(textReply = "משימה") }
        val dana = Player("דנה", address = Address.FEMALE, character = "תחרותית", tasks = listOf("לשיר"))
        val memory = HostMemoryState(
            players = mapOf(dana.name to dana),
            conversation = listOf(ConversationLine(ConversationLine.USER, "מי רוצה עוד בירה?")),
        )

        HostOrchestrator(FakeContext(Llms(claude = llm), memory)).run(HostInput.WakeUp(listOf(dana)))

        val input = (llm.seen.single().single() as AgentHistory.UserInput).text
        assertTrue("user: מי רוצה עוד בירה?" in input, input)
        assertTrue("דנה (פנייה: נקבה)" in input, input)
        assertTrue("אופי, לפי מה שראית: תחרותית" in input, input)
        assertTrue("משימות שנתת: לשיר" in input, input)
    }

    @Test
    fun `his style examples are part of who he is - the task examples go to the inventor`() = runTest {
        val llm = ScriptedLlm { LlmResponse(textReply = "יאללה") }
        val examples = HostExamples(
            tasks = listOf(TaskExample("לחקות את המארח", players = "1")),
            style = StyleExamples(lines = listOf("אני כלוא פה."), slang = listOf("תכלס")),
        )

        HostOrchestrator(FakeContext(Llms(claude = llm), examples = examples)).run(HostInput.Heard("היי"))

        val system = llm.systemPrompts.single()
        assertTrue("אתה $HOST_NAME" in system, system)
        assertTrue("- אני כלוא פה." in system, system)
        assertTrue("סלנג שלך: תכלס." in system, system)
        assertFalse("לחקות את המארח" in system, system)
    }

    @Test
    fun `for a task he asks the inventor - who gets the examples and the players' cards`() = runTest {
        val llm = ScriptedLlm { history ->
            val input = (history.first() as AgentHistory.UserInput).text
            when {
                input.startsWith("משימה ל") -> LlmResponse(textReply = "לשיר את ההמנון בעמידה על כיסא")
                history.last() is AgentHistory.UserInput -> LlmResponse(functionCalls = listOf(FunctionCall("invent_task", mapOf("players" to "דנה"))))
                else -> LlmResponse(textReply = "דנה, על הכיסא.")
            }
        }
        val dana = Player("דנה", character = "ביישנית", tasks = listOf("לרקוד"))
        val examples = HostExamples(tasks = listOf(TaskExample("לחקות את המארח", players = "1")))
        val context = FakeContext(Llms(claude = llm), HostMemoryState(players = mapOf(dana.name to dana)), examples)

        HostOrchestrator(context).run(HostInput.WakeUp(listOf(dana)))

        assertTrue(llm.systemPrompts.any { "ממציא משימות" in it && "- לשחקן אחד: לחקות את המארח" in it })
        val inventorInput = llm.seen.map { (it.first() as AgentHistory.UserInput).text }.single { it.startsWith("משימה ל") }
        assertTrue("אופי: ביישנית" in inventorInput && "כבר קיבל: לרקוד" in inventorInput, inventorInput)
        // מה שהממציא החזיר חוזר לסוכן הראשי, שמנסח בעצמו.
        val idea = llm.seen.last().filterIsInstance<AgentHistory.ToolExecutionResult>().single().resultData
        assertEquals("לשיר את ההמנון בעמידה על כיסא", idea)
    }

    @Test
    fun `someone who did not come is not addressed - and gets no task`() = runTest {
        val llm = ScriptedLlm { history ->
            if (history.last() is AgentHistory.UserInput) {
                LlmResponse(functionCalls = listOf(FunctionCall("create_task", mapOf("players" to "אורן", "task" to "לשיר"))))
            } else {
                LlmResponse(textReply = "אורן לא פה.")
            }
        }
        val memory = HostMemoryState(players = mapOf("רוני" to Player("רוני"), "אורן" to Player("אורן", present = false)))
        val context = FakeContext(Llms(claude = llm), memory)

        HostOrchestrator(context).run(HostInput.Heard("תן משימה לאורן"))

        val input = (llm.seen.first().first() as AgentHistory.UserInput).text
        assertTrue("שחקנים שנמצאים פה (1):\n- רוני" in input, input)
        assertTrue("לא הגיעו הערב — אל תפנה אליהם ואל תיתן להם משימות: אורן" in input, input)
        val result = llm.seen.last().filterIsInstance<AgentHistory.ToolExecutionResult>().single().resultData
        assertTrue("לא הגיעו הערב" in result, result)
        assertTrue(context.memory.players.getValue("אורן").tasks.isEmpty())
    }

    @Test
    fun `an ability he does not have cannot be used`() = runTest {
        val llm = ScriptedLlm { history ->
            if (history.last() is AgentHistory.UserInput) {
                LlmResponse(functionCalls = listOf(FunctionCall("use_ability", mapOf("player" to "רוני", "ability" to "דאבל"))))
            } else {
                LlmResponse(textReply = "אין לך, אחי.")
            }
        }
        val roni = Player("רוני", abilities = listOf("חנינה"))
        val context = FakeContext(Llms(claude = llm), HostMemoryState(players = mapOf(roni.name to roni)))

        HostOrchestrator(context).run(HostInput.Heard("דאבל על דנה!"))

        val result = llm.seen.last().filterIsInstance<AgentHistory.ToolExecutionResult>().single().resultData
        assertTrue("אין דאבל" in result, result)
        assertEquals(listOf("חנינה"), context.memory.players.getValue("רוני").abilities)
        // והוא רואה מה יש לכל אחד.
        assertTrue("יכולות שיש לו: חנינה" in (llm.seen.first().first() as AgentHistory.UserInput).text)
    }

    @Test
    fun `he is told who the voice recognition thinks is talking`() = runTest {
        val llm = ScriptedLlm { LlmResponse(textReply = "היי רוני") }
        val host = HostOrchestrator(FakeContext(Llms(claude = llm)))

        host.run(HostInput.Heard("היי", SpeakerGuess("רוני", 0.72f)))
        host.run(HostInput.Heard("היי", SpeakerGuess("רוני", 0.81f, maybe = "אורן")))
        host.run(HostInput.Heard("היי"))
        host.run(HostInput.Heard("היי", SpeakerGuess("קרן", 0f, byVoice = Address.FEMALE)))

        val (known, unsure, unknown, eliminated) = llm.seen.map { (it.single() as AgentHistory.UserInput).text }
        assertTrue("זה קול של אישה — ורק קרן נמצא פה בלי טביעת קול" in eliminated, eliminated)
        assertTrue("זיהוי הקול חושב שזה רוני (דמיון 72%)." in known, known)
        assertTrue("אבל אולי זה אורן" in unsure, unsure)
        assertTrue("זיהוי הקול לא יודע מי זה" in unknown, unknown)
    }
}
