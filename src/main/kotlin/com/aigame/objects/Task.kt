package com.aigame.objects

// משימה שהסוכן נותן — גם קנס. נרשמת אצל כל שחקן שקיבל אותה (players.json),
// ואין רשימת משימות נפרדת. אם בוצעה או נשתה צ'ייסר — לא נדע לעולם.
data class Task(
    val players: List<String>,
    val text: String,
)
