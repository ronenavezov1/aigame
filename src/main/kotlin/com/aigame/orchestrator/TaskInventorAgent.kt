package com.aigame.orchestrator

import com.aiagent.lib.agent.AgentConfig
import com.aiagent.lib.agent.AiAgent
import com.aiagent.lib.agent.Tool
import com.aiagent.lib.llm.LlmClient
import com.aiagent.lib.llm.ModelTier
import com.aigame.objects.Player
import com.aigame.objects.TaskExample

// ============================================================
//  תת-סוכן: ממציא משימות.
//
//  הסוכן הראשי שואל אותו "משימה לרוני ולדנה", והוא מחזיר משימה אחת:
//  בהשראת הדוגמאות שכתבת, מותאמת לרקע ולאופי שלהם, ולא כזו שכבר
//  קיבלו. את הניסוח בקול ואת הרישום (create_task) עושה הסוכן הראשי.
//
//  את המודל והדרגה בוחר HostOrchestrator.
// ============================================================

internal class TaskInventorAgent(
    private val context: HostOrchestratorContext,
    private val llm: LlmClient,
    private val tier: ModelTier,
) {
    suspend fun run(players: List<Player>): String {
        val agent = AiAgent(
            AgentConfig(
                agentId = "task-inventor",
                llmClient = llm,
                tier = tier,
                systemPrompt = PROMPT + examples(context.examples.tasks),
                timeoutMs = 30_000,
                maxDepth = 1,
            )
        )
        return agent.processInput(input(players)).trim()
    }

    private fun input(players: List<Player>): String = buildString {
        appendLine("משימה ל-${players.size} שחקנים:")
        players.forEach { p ->
            appendLine("- ${p.name} (פנייה: ${p.address.hebrew})")
            if (p.background.isNotBlank()) appendLine("  רקע: ${p.background}")
            if (p.character.isNotBlank()) appendLine("  אופי: ${p.character}")
            if (p.tasks.isNotEmpty()) appendLine("  כבר קיבל: ${p.tasks.joinToString("; ")}")
        }
    }

    private fun examples(tasks: List<TaskExample>): String =
        if (tasks.isEmpty()) "" else "\n\nמשימות לדוגמה — השראה, לא רשימה סגורה:" + tasks.joinToString("") { "\n- ${it.forWhom}: ${it.text}" }

    private companion object {
        val PROMPT = """
            אתה ממציא משימות לערב משחקי שתייה של חבורת חברים. אתה לא מדבר עם אף אחד — רק מחזיר משימה אחת.
            - משפט אחד, ברור, שאפשר לבצע עכשיו בסלון.
            - מותאמת לשחקנים: לפי הרקע והאופי שלהם.
            - לא משימה שהם כבר קיבלו, ולא כמעט אותה.
            - כל משימה אפשר להמיר בצ'ייסר.
            החזר רק את המשימה, בלי הקדמה.
        """.trimIndent()
    }
}

/** הכלי שהסוכן הראשי רואה: invent_task — ומאחוריו התת-סוכן. */
internal class InventTaskTool(
    private val context: HostOrchestratorContext,
    private val inventor: TaskInventorAgent,
) : Tool {

    override val name = "invent_task"

    override val description =
        "מבקש מממציא המשימות רעיון למשימה, מותאם לשחקנים. מחזיר משימה אחת — " +
            "אתה מנסח אותה בסגנון שלך, ורושם עם create_task."

    override val parameters = mapOf(
        "players" to "למי: שמות של שחקנים רשומים, מופרדים בפסיק.",
    )

    override suspend fun execute(args: Map<String, Any>): String {
        val asked = args["players"]?.toString().orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val players = asked.mapNotNull { context.memory.player(it) }
        if (players.isEmpty()) return "אין שחקנים כאלה. הרשומים: ${context.memory.players.keys.joinToString()}"
        return inventor.run(players)
    }
}
