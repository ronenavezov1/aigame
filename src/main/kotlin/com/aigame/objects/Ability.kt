package com.aigame.objects

// יכולת מיוחדת — פרס מהסוכן. השחקן משתמש בה מתי שבא לו, פעם אחת, והיא נמחקת.
// בקובץ השחקנים נשמר השם העברי.
enum class Ability(val hebrew: String, val description: String) {
    DOUBLE("דאבל", "על מישהו אחר: בפעם הבאה שהוא שותה — הוא שותה כפול"),
    PARDON("חנינה", "מבטלת משימה או צ'ייסר אחד"),
    PASS("מעביר", "מעבירה משימה או צ'ייסר למישהו אחר"),
    ;

    companion object {
        fun parse(text: String): Ability? =
            entries.firstOrNull { it.hebrew == text.trim() || it.name.equals(text.trim(), ignoreCase = true) }

        /** כל היכולות, לתיאור לסוכן. */
        val menu: String get() = entries.joinToString("; ") { "${it.hebrew} — ${it.description}" }
    }
}
