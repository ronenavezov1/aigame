package com.aigame

import com.aigame.objects.ConversationLine
import com.aigame.objects.HostEvent
import com.aigame.objects.SpeakerGuess
import com.aigame.repository.AgentRepository
import com.aigame.repository.FillerRepository
import com.aigame.repository.HostRepository
import com.aigame.repository.MemoryRepository
import com.aigame.repository.VoiceRepository
import com.aigame.speech.NoTextToSpeech
import com.aigame.state.AppStateManager
import com.aigame.state.Phase
import com.aigame.storage.MemoryStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Files
import kotlin.test.Test

/**
 * שיחה אמיתית מול Claude: HostRepository → AgentRepository → HostOrchestrator.
 *
 * רצה רק עם LIVE_HOST_CHECK=1 ועם מפתח ב-.env — היא עולה כמה סנטים.
 * הזיכרון נכתב לתיקייה זמנית, לא ל-data/ של המסיבה.
 *
 *   LIVE_HOST_CHECK=1 ./gradlew test --tests '*LiveHostCheck*'
 */
class LiveHostCheck {

    @Test
    fun conversation() = runBlocking {
        if (System.getenv("LIVE_HOST_CHECK") == null) return@runBlocking

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val appStateManager = AppStateManager()
            val dir = Files.createTempDirectory("live-host")
            val memory = MemoryRepository(appStateManager, MemoryStorage(dir)).also { it.load() }
            val agent = AgentRepository(appStateManager, memory)
            check(agent.isConfigured) { "אין ANTHROPIC_API_KEY ב-.env" }
            val voice = VoiceRepository(NoTextToSpeech)
            val fillers = FillerRepository(NoTextToSpeech, dir.resolve("fillers"))
            val host = HostRepository(appStateManager, agent, memory, voice, fillers, scope).also { it.start() }

            // במקום מסך: מדפיס מה המארח אומר, ומדווח שסיים להקריא.
            scope.launch {
                appStateManager.hostState.collect { state ->
                    if (state.phase == Phase.SPEAKING) {
                        println("=== הבעה: ${state.expression}\n    ${state.subtitle}")
                        host.speechFinished()
                    }
                }
            }

            // תור נגמר כשנוספו לשיחה שתי שורות — הקלט והתשובה.
            suspend fun turn(label: String, send: () -> Unit) {
                val lines = appStateManager.hostMemoryState.value.conversation.size
                val started = System.currentTimeMillis()
                send()
                withTimeout(90_000) {
                    appStateManager.hostMemoryState.first { it.conversation.size >= lines + 2 }
                }
                println("=== $label · ${System.currentTimeMillis() - started}ms")
            }

            turn("דנה מציגה את עצמה") { host.hear("היי! אני דנה, חוגגת היום 30") }
            turn("רוני מציג את עצמו") { host.hear("ואני רוני, אני זה שכתב אותך") }
            // מכאן זיהוי הקול "מזהה" את רוני — ומנתח השחקנים רץ אחרי כל הודעה שלו.
            fun roni(text: String) = appStateManager.addHostEvent(HostEvent.Heard(text, SpeakerGuess("רוני", 0.8f)))
            turn("רוני מתחכם") { roni("אתה סתם תוכנה, אתה לא יכול לעשות לי כלום. ואני מהנדס, אני מבין בזה") }
            turn("רוני שואל") { roni("טוב, סליחה כבוד השופט. מה שלומך היום?") }
            turn("התעוררות לבד") { host.wakeNow() }

            // מנתח השחקנים רץ ברקע — מחכים שיסיים לפני שמדפיסים.
            withTimeoutOrNull(60_000) { appStateManager.hostMemoryState.first { it.player("רוני")?.character?.isNotBlank() == true } }

            val end = appStateManager.hostMemoryState.value
            end.players.values.forEach { p ->
                println("=== ${p.name}: רקע «${p.background}» · אופי «${p.character}» · משימות ${p.tasks} · יכולות ${p.abilities}")
            }
            println("=== שיחה:\n" + end.conversation.joinToString("\n") { "    ${it.who}: ${it.text}" })
            println("=== הקבצים: $dir")
            check(end.conversation.count { it.who == ConversationLine.AGENT } >= 5)
        } finally {
            scope.cancel()
        }
    }
}
