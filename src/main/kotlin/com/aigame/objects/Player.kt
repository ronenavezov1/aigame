package com.aigame.objects

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// שחקן אחד. נשמר ב-players.json — המפתחות בעברית, כדי שאפשר יהיה לקרוא את הקובץ.
@Serializable
data class Player(
    @SerialName("שם") val name: String,
    // הגיע הערב? מי שלא — לא פונים אליו, לא מגרילים אותו ולא מזהים את הקול שלו. מסמנים בהגדרות.
    @SerialName("נוכח") val present: Boolean = true,
    // מי הוא — מה שסיפר על עצמו או שסיפרו עליו. הסוכן מעדכן כשהוא מגלה עוד.
    @SerialName("רקע") val background: String = "",
    // איך הסוכן מסווג את האישיות שלו. רק הסוכן ממלא — במהלך המשחק.
    @SerialName("אופי") val character: String = "",
    @SerialName("פנייה") val address: Address = Address.UNKNOWN,
    // כמה פעמים נבחר בהגרלה. ההגרלה מעדיפה את מי שנבחר פחות.
    @SerialName("נבחר בהגרלה") val picks: Int = 0,
    // המשימות שקיבל. אם ביצע — לא נדע לעולם.
    @SerialName("משימות") val tasks: List<String> = emptyList(),
    // יכולות מיוחדות שזכה בהן (Ability, בעברית). כל אחת — שימוש אחד.
    @SerialName("יכולות") val abilities: List<String> = emptyList(),
)

// איך פונים אליו בעברית. בלי זה המארח מנחש לפי השם, ומנחש לא נכון.
@Serializable
enum class Address(val hebrew: String) {
    @SerialName("זכר") MALE("זכר"),
    @SerialName("נקבה") FEMALE("נקבה"),
    @SerialName("לא ידוע") UNKNOWN("לא ידוע");

    companion object {
        fun parse(text: String): Address = entries.firstOrNull { it.hebrew == text.trim() } ?: UNKNOWN
    }
}
