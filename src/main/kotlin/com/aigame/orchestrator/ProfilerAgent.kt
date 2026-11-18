package com.aigame.orchestrator

import com.aiagent.lib.agent.Action
import com.aiagent.lib.agent.AgentConfig
import com.aiagent.lib.agent.AiAgent
import com.aiagent.lib.llm.LlmClient
import com.aiagent.lib.llm.ModelTier
import com.aigame.objects.ConversationLine
import com.aigame.objects.Player
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

// ============================================================
//  תת-סוכן: מנתח שחקנים.
//
//  אחרי כל הודעה של שחקן מזוהה — ברקע, בלי לעכב את התשובה:
//    1. בודק שההודעה באמת מתאימה לו — זיהוי הקול יכול לטעות.
//    2. מעדכן את הרקע והאופי שלו — ב-state ובקובץ (דרך ה-context).
//  ככה המארח עצמו לא מתעכב על זה, והכרטיס של כל אחד מתמלא לבד.
//
//  את המודל והדרגה בוחר HostOrchestrator.
// ============================================================

internal class ProfilerAgent(
    private val context: HostOrchestratorContext,
    private val llm: LlmClient,
    private val tier: ModelTier,
) {
    suspend fun run(name: String) {
        val player = context.memory.player(name) ?: return
        val agent = AiAgent(
            AgentConfig(
                agentId = "profiler",
                workOnId = player.name,
                llmClient = llm,
                tier = tier,
                systemPrompt = PROMPT,
                actions = listOf(UpdateProfileAction(context, player.name)).associateBy { it.name },
                timeoutMs = 60_000,
                maxDepth = 3,
            )
        )
        agent.processInput(input(player, context.memory.conversation))
    }

    private fun input(player: Player, conversation: List<ConversationLine>): String = buildString {
        appendLine("השחקן: ${player.name}")
        appendLine("רקע עד עכשיו: ${player.background.ifBlank { "אין" }}")
        appendLine("אופי עד עכשיו: ${player.character.ifBlank { "אין" }}")
        conversation.lastOrNull { it.who == player.name }?.let {
            appendLine("ההודעה האחרונה, שזיהוי הקול שייך לו: «${it.text}»")
        }
        appendLine()
        appendLine("השיחה ברבע השעה האחרונה (${ConversationLine.AGENT} = המארח, ${ConversationLine.USER} = אורח שלא זוהה):")
        conversation.forEach { appendLine("[${CLOCK.format(Instant.ofEpochMilli(it.at))}] ${it.who}: ${it.text}") }
    }

    private companion object {
        val PROMPT = """
            אתה מנתח אישיות במסיבה. אתה לא מדבר עם אף אחד — רק מעדכן את הכרטיס של שחקן אחד.
            קודם בדוק שההודעה האחרונה באמת שלו: זיהוי הקול יכול לטעות. אם מהשיחה נראה שמישהו אחר
            אמר אותה — למשל הוא הציג את עצמו בשם אחר — אל תעדכן, וענה "לא בטוח שזה הוא".
            אחרת, קרא את השיחה ואת מה שכבר ידוע עליו, ועדכן עם update_profile:
            - רקע: עובדות עליו שכדאי לזכור — עבודה, גיל, קשרים, מה הוא אוהב. עד שלושה משפטים קצרים, בלי שעות
              ובלי תיאור של כל מה שקרה הערב. כל מה שחשוב, לא רק החדש.
            - אופי: איך הוא מתנהג — למשל תחרותי, ביישן, מתחכם, ליצן, רגיש. עד עשר מילים.
            רק ממה שנאמר — אל תמציא. אם לא למדת עליו שום דבר חדש, אל תעדכן, וענה "אין חדש".
        """.trimIndent()
    }
}

/** הכרטיס של שחקן אחד — רק שלו: השם קבוע מראש, והתת-סוכן לא יכול לגעת באחרים. */
private class UpdateProfileAction(private val context: HostOrchestratorContext, private val player: String) : Action {

    override val name = "update_profile"

    override val description = "מעדכן את הרקע והאופי של השחקן. פרט שתשאיר ריק — נשאר כמו שהיה."

    override val parameters = mapOf(
        "background" to "עובדות עליו — כל מה שידוע, לא רק החדש. ריק = בלי שינוי.",
        "character" to "איך הוא מתנהג — כל מה שאתה חושב, לא רק החדש. ריק = בלי שינוי.",
    )

    override suspend fun perform(agentId: String, args: Map<String, Any>): String {
        context.addPlayer(
            Player(
                name = player,
                background = args["background"]?.toString()?.trim().orEmpty(),
                character = args["character"]?.toString()?.trim().orEmpty(),
            )
        )
        return "הכרטיס של $player עודכן."
    }
}
