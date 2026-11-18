package com.aigame

import com.aigame.objects.TaskExample
import com.aigame.storage.ExamplesStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** קבצי הדוגמאות שב-resources נקראים. JSON שבור — הבדיקה נופלת, לא המסיבה. */
class ExamplesStorageTest {

    @Test
    fun `the example files you wrote load`() {
        val examples = ExamplesStorage.load()
        assertTrue(examples.tasks.isNotEmpty(), "task-examples.json ריק או שבור")
        assertTrue(examples.tasks.all { it.players.isNotBlank() }, "משימה בלי \"שחקנים\"")
        assertTrue(examples.style.slang.none { it.isBlank() }, "סלנג ריק הגיע לסוכן")
        assertTrue(examples.style.lines.isNotEmpty(), "style-examples.json ריק או שבור")
    }

    @Test
    fun `for whom - a number or words`() {
        assertEquals("לשחקן אחד", TaskExample("x", "1").forWhom)
        assertEquals("ל-3 שחקנים", TaskExample("x", "3").forWhom)
        assertEquals("כל הבנים", TaskExample("x", "כל הבנים").forWhom)
    }
}
