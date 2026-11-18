package com.aigame.storage

import com.aigame.objects.HostExamples
import com.aigame.objects.StyleExamples
import com.aigame.objects.TaskExample
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("ExamplesStorage")

// ============================================================
//  הדוגמאות לסוכן — קבצי JSON ב-src/main/resources, שאתה כותב:
//
//      task-examples.json    משימות לדוגמה
//      style-examples.json   משפטים וסלנג באופי שלו
//
//  נקראים פעם אחת בהדלקה. קובץ חסר או פגום — בלי הדוגמאות שבו,
//  והסוכן מסתדר בלעדיהן (ExamplesStorageTest יתפוס את זה קודם).
// ============================================================

object ExamplesStorage {

    private val json = Json {
        ignoreUnknownKeys = true
        // "שחקנים": 2 וגם "שחקנים": "כולם" — מספר בלי מירכאות נקרא כטקסט.
        isLenient = true
    }

    /** שורות ריקות — שארית של עריכה — לא מגיעות לסוכן. */
    fun load(): HostExamples {
        val tasks = read("task-examples.json", ListSerializer(TaskExample.serializer())).orEmpty()
        val style = read("style-examples.json", StyleExamples.serializer()) ?: StyleExamples()
        return HostExamples(
            tasks = tasks.filter { it.text.isNotBlank() },
            style = StyleExamples(lines = style.lines.filter { it.isNotBlank() }, slang = style.slang.filter { it.isNotBlank() }),
        )
    }

    private fun <T> read(name: String, serializer: KSerializer<T>): T? {
        val text = ExamplesStorage::class.java.getResource("/$name")?.readText()
        if (text == null) {
            log.warn("אין {} — בלי הדוגמאות שבו", name)
            return null
        }
        return try {
            json.decodeFromString(serializer, text)
        } catch (e: Exception) {
            log.error("{} פגום — בלי הדוגמאות שבו: {}", name, e.message)
            null
        }
    }
}
