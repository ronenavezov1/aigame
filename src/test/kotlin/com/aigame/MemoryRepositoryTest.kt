package com.aigame

import com.aigame.objects.ConversationLine
import com.aigame.repository.MemoryRepository
import com.aigame.state.AppStateManager
import com.aigame.storage.MemoryStorage
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class MemoryRepositoryTest {

    private val appStateManager = AppStateManager()
    private var now = 0L
    private val memory = MemoryRepository(appStateManager, MemoryStorage(Files.createTempDirectory("memory-repo-test")), now = { now })

    @Test
    fun `a new message throws away what was said more than 15 minutes ago`() {
        memory.addConversation(line("שלום", at = 0), line("היי", at = 0))
        now = 10 * MINUTE
        memory.addConversation(line("מה נשמע", at = now), line("סבבה", at = now))

        now = 16 * MINUTE // הגיעה הודעה חדשה
        memory.forgetOldConversation()

        assertEquals(listOf("מה נשמע", "סבבה"), appStateManager.hostMemoryState.value.conversation.map { it.text })
    }

    @Test
    fun `a quiet half hour - the agent starts with a clean conversation`() {
        memory.addConversation(line("שלום", at = 0), line("היי", at = 0))

        now = 30 * MINUTE
        memory.forgetOldConversation()

        assertEquals(emptyList(), appStateManager.hostMemoryState.value.conversation)
    }

    private fun line(text: String, at: Long) = ConversationLine(ConversationLine.USER, text, at)

    private companion object {
        const val MINUTE = 60_000L
    }
}
