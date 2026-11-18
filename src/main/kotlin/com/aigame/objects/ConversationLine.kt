package com.aigame.objects

// שורה אחת בשיחה: מתי, מי דיבר ומה אמר. רק בזיכרון — לא נשמרת לקובץ,
// ונזרקת אחרי רבע שעה (MemoryRepository).
data class ConversationLine(
    val who: String,
    val text: String,
    val at: Long = System.currentTimeMillis(),
) {
    companion object {
        /** אורח שלא זוהה. */
        const val USER = "user"

        /** הסוכן — גם כשהוא עונה, וגם כשהוא מתעורר לבד. */
        const val AGENT = "סוכן"
    }
}
