package com.aigame.storage

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

// ============================================================
//  קובץ JSON אחד שמחזיק ערך אחד.
//
//  הכתיבה אטומית: כותבים לקובץ זמני באותה תיקייה, מכריחים
//  אותו לדיסק (force), ורק אז מחליפים בשם. אם החשמל נופל באמצע,
//  נשאר או הקובץ הישן או החדש — אף פעם לא חצי קובץ.
//
//  בלי ה-force, מערכת ההפעלה יכולה להחזיק את התוכן בזיכרון
//  שניות ארוכות אחרי שה-move כבר נרשם — ואז אחרי נפילת חשמל
//  מקבלים קובץ בשם הנכון ובלי תוכן. בדיוק המקרה שבשבילו זה קיים.
// ============================================================

private val log = LoggerFactory.getLogger("FileStore")

class FileStore<T>(
    private val file: Path,
    private val serializer: KSerializer<T>,
) {
    private val json = Json {
        prettyPrint = true
        // בלי זה ברירות מחדל לא נכתבות — ו-startedAt היה מחושב
        // מחדש בכל טעינה, כאילו המסיבה התחילה עכשיו.
        encodeDefaults = true
        // קובץ שנכתב בגרסה חדשה יותר עדיין נטען בישנה.
        ignoreUnknownKeys = true
        // הקבצים נערכים גם ביד: ערך לא מוכר (למשל "פנייה": "גבר") או null
        // הופך לברירת המחדל, במקום להעביר את כל הקובץ הצידה.
        coerceInputValues = true
    }

    /**
     * הערך השמור, או null אם אין.
     *
     * קובץ פגום לא מפיל את האפליקציה: הוא עובר הצידה עם סיומת
     * broken, ומתחילים מאפס. עדיף ערב בלי זיכרון מאשר מסך שחור —
     * והקובץ עדיין שם אם רוצים להציל ממנו משהו.
     */
    fun read(): T? {
        if (!Files.exists(file)) return null
        return try {
            json.decodeFromString(serializer, Files.readString(file))
        } catch (e: Exception) {
            val aside = file.resolveSibling("${file.fileName}.broken-${System.currentTimeMillis()}")
            Files.move(file, aside)
            log.error("{} פגום ({}). הועבר ל-{} ומתחילים מאפס", file, e.message, aside)
            null
        }
    }

    /** synchronized: שני שינויים שמגיעים ביחד לא כותבים את הקובץ באותו רגע. */
    @Synchronized
    fun write(value: T) {
        Files.createDirectories(file.parent)
        val tmp = Files.createTempFile(file.parent, "${file.fileName}.", ".tmp")
        try {
            FileChannel.open(tmp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use { ch ->
                ch.write(java.nio.ByteBuffer.wrap(json.encodeToString(serializer, value).toByteArray()))
                ch.force(true)
            }
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(tmp)
        }
    }
}
