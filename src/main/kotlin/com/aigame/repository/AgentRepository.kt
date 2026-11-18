package com.aigame.repository

import com.aiagent.lib.llm.AnthropicLlmClient
import com.aiagent.lib.llm.GoogleLlmClient
import com.aiagent.lib.llm.LlmNonFatalException
import com.aiagent.lib.llm.LlmSendException
import com.aiagent.lib.llm.LocalLlmClient
import com.aiagent.lib.llm.RetryPolicy
import com.aigame.Env
import com.aigame.objects.Ability
import com.aigame.objects.HostExamples
import com.aigame.objects.Player
import com.aigame.objects.Task
import com.aigame.orchestrator.HostInput
import com.aigame.orchestrator.HostOrchestrator
import com.aigame.orchestrator.HostOrchestratorContext
import com.aigame.orchestrator.HostOutput
import com.aigame.orchestrator.Llms
import com.aigame.state.AppStateManager
import com.aigame.state.HostMemoryState
import com.aigame.storage.ExamplesStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("AgentRepository")

// מה המארח אומר כשהוא לא יכול לחשוב. קבוע — אין מודל שיכתוב אותו.
private const val NO_BRAIN = "אין לי מוח עדיין. מישהו צריך לשים מפתח בקובץ .env."
private const val BROKEN_BRAIN = "רגע, המוח שלי נתקע. תנסו שוב עוד שנייה."
private const val SLOW_BRAIN = "חשבתי יותר מדי ושכחתי מה רציתי להגיד. עוד פעם?"
private const val REFUSED = "על זה אני מדלג. מישהו אחר?"

// במסיבה עדיף להיכשל מהר ולהגיד משהו: ניסיון חוזר אחד, אחרי שנייה.
private val PARTY_RETRY = RetryPolicy(maxAttempts = 2, firstDelayMs = 1_000)

// המוח: מחזיק את כל ה-LLM-ים, מפעיל את הסוכן (HostOrchestrator), ותמיד מחזיר פלט.
// כשהמוח לא עונה, המארח עדיין אומר משהו — עדיף "המוח שלי נתקע" מאשר פרצוף קפוא.
//
// הוא גם ה-context של הסוכן: הזיכרון מה-state, הדוגמאות מהקבצים, ושחקן ומשימה
// נכתבים דרך MemoryRepository.
class AgentRepository(
    private val stateManager: AppStateManager,
    private val memoryRepository: MemoryRepository,
    override val llms: Llms = buildLlms(),
    override val examples: HostExamples = ExamplesStorage.load(),
) : HostOrchestratorContext {

    override val memory: HostMemoryState get() = stateManager.hostMemoryState.value

    override fun addPlayer(player: Player) = memoryRepository.addPlayer(player)

    override fun addTask(task: Task) = memoryRepository.addTask(task)

    override fun grantAbility(player: String, ability: Ability) = memoryRepository.grantAbility(player, ability)

    override fun useAbility(player: String, ability: Ability) = memoryRepository.useAbility(player, ability)

    /** המארח רץ על Claude — בלעדיו אין מוח. */
    val isConfigured: Boolean get() = llms.claude != null

    suspend fun think(input: HostInput): HostOutput {
        if (!isConfigured) return HostOutput(NO_BRAIN)

        return try {
            HostOrchestrator(this).run(input)
                .also { stateManager.updateEnvironment { it.copy(brainError = null) } }
        } catch (e: TimeoutCancellationException) {
            // ה-timeout של הסוכן עצמו, לא ביטול של הסקופ — זה עובר הלאה.
            log.warn("המארח לא סיים בזמן")
            HostOutput(SLOW_BRAIN)
        } catch (e: LlmNonFatalException) {
            log.warn("סירוב: {}", e.errorMessage)
            HostOutput(REFUSED)
        } catch (e: LlmSendException) {
            log.error("המארח נכשל: {}", e.errorMessage)
            stateManager.updateEnvironment { it.copy(brainError = e.errorMessage) }
            HostOutput(BROKEN_BRAIN)
        }
    }

    /** מנתח השחקנים: מעדכן רקע ואופי של מי שדיבר. רץ ברקע — כישלון רק נרשם ביומן. */
    suspend fun profile(name: String) {
        if (!isConfigured) return
        try {
            HostOrchestrator(this).profile(name)
        } catch (e: TimeoutCancellationException) {
            log.warn("מנתח השחקנים לא סיים בזמן ({})", name)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("מנתח השחקנים נכשל ({}): {}", name, e.message)
        }
    }

    companion object {
        /** כל LLM שיש לו מפתח. איזה מהם מקבל כל סוכן — HostOrchestrator מחליט. */
        private fun buildLlms(): Llms {
            val anthropicKey = Env.get("ANTHROPIC_API_KEY")
            if (anthropicKey == null) log.warn("אין ANTHROPIC_API_KEY — המארח לא יחשוב")
            val googleKey = Env.get("GOOGLE_API_KEY")

            return Llms(
                claude = anthropicKey?.let {
                    AnthropicLlmClient(
                        apiKey = it,
                        workspaceId = Env.get("ANTHROPIC_WORKSPACE_ID"),
                        serverFallback = Env.get("ANTHROPIC_FALLBACKS", "true") != "false",
                        retry = PARTY_RETRY,
                    )
                },
                gemini = googleKey?.let {
                    GoogleLlmClient(apiKey = it, fastModel = "gemini-2.5-flash", smartModel = "gemini-2.5-pro", retry = PARTY_RETRY)
                },
                local = LocalLlmClient(),
            )
        }
    }
}
