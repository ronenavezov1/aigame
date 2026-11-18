package com.aigame

import org.slf4j.LoggerFactory
import java.io.File

// ============================================================
//  קריאת הגדרות, עם נפילה ל-.env אם הן לא בסביבה.
//
//  אותו קובץ כמו בשרת של הדייט. כאן הוא עוד יותר חשוב: ./gradlew
//  run, הכפתור הירוק של IntelliJ והרצה מ-jar מגיעים כל אחד עם
//  סביבה אחרת, ו-.env בתיקיית הפרויקט הוא המכנה המשותף.
//
//  סדר העדיפות: משתנה סביבה אמיתי תמיד גובר על הקובץ.
//
//  (ב-JVM אי אפשר לשנות את סביבת התהליך אחרי שהוא עלה,
//  ולכן זו פונקציית קריאה ולא הזרקה ל-System.getenv.)
// ============================================================

private val log = LoggerFactory.getLogger("Env")

object Env {

    private val fromFile: Map<String, String> by lazy {
        val file = File(".env")
        if (!file.exists()) return@lazy emptyMap()

        val values = file.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
            .associate { line ->
                val (key, value) = line.split("=", limit = 2)
                key.trim() to value.trim().trim('"', '\'')
            }

        log.info("נטענו {} הגדרות מ-.env", values.size)
        values
    }

    /** משתנה סביבה, ואם אין — מה שכתוב ב-.env. */
    fun get(key: String): String? =
        System.getenv(key)?.takeIf { it.isNotBlank() } ?: fromFile[key]?.takeIf { it.isNotBlank() }

    fun get(key: String, default: String): String = get(key) ?: default
}
