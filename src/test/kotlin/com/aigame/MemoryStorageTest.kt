package com.aigame

import com.aigame.objects.Address
import com.aigame.objects.Player
import com.aigame.storage.MemoryStorage
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemoryStorageTest {

    private val dir = Files.createTempDirectory("memory-test")

    @Test
    fun `players and the clock come back after a restart - the conversation does not`() {
        val storage = MemoryStorage(dir)
        val startedAt = storage.load().clock.startedAt
        storage.savePlayers(listOf(Player("דנה", background = "חוגגת יום הולדת", tasks = listOf("לשיר"))))

        // "הפעלה מחדש": קריאה טרייה של אותם קבצים.
        val memory = MemoryStorage(dir).load()
        assertEquals("חוגגת יום הולדת", memory.players.getValue("דנה").background)
        assertEquals(listOf("לשיר"), memory.players.getValue("דנה").tasks)
        assertTrue(memory.conversation.isEmpty())
        // המסיבה לא התחילה מחדש.
        assertEquals(startedAt, memory.clock.startedAt)
        // הקובץ נקרא גם בעין: מפתחות בעברית.
        assertTrue("\"רקע\"" in Files.readString(dir.resolve("players.json")))
    }

    @Test
    fun `a hand-edited player with a wrong address still loads`() {
        Files.writeString(dir.resolve("players.json"), """[{ "שם": "רוני", "רקע": "כתב את הסוכן", "פנייה": "גבר" }]""")

        val roni = MemoryStorage(dir).load().players.getValue("רוני")
        assertEquals("כתב את הסוכן", roni.background)
        assertEquals(Address.UNKNOWN, roni.address)
    }

    @Test
    fun `a player without a name is skipped, a repeated name keeps the last`() {
        Files.writeString(
            dir.resolve("players.json"),
            """[{ "שם": "" }, { "שם": "רוני", "רקע": "ישן" }, { "שם": " רוני ", "רקע": "חדש" }, { "שם": "דנה" }]""",
        )

        val players = MemoryStorage(dir).load().players
        assertEquals(listOf("רוני", "דנה"), players.keys.toList())
        assertEquals("חדש", players.getValue("רוני").background)
    }

    @Test
    fun `corrupt players file is moved aside instead of crashing`() {
        Files.writeString(dir.resolve("players.json"), "{ זה לא JSON")

        assertTrue(MemoryStorage(dir).load().players.isEmpty())
        assertTrue(Files.list(dir).anyMatch { it.fileName.toString().startsWith("players.json.broken-") })
    }
}
