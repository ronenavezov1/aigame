package com.aigame.state

// טביעות הקול של השחקנים, ומי עושה עכשיו איתחול.
data class VoiceprintState(
    val voiceprints: Map<String, List<Float>> = emptyMap(), // לפי שם
    // מי מקריא עכשיו את טקסט האיתחול. null = אף אחד — הקלטות הולכות למארח.
    val enrolling: String? = null,
    // מה השתבש באיתחול האחרון — המסך מציג. null = הכל בסדר.
    val problem: String? = null,
)
