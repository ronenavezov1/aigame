package com.aigame.objects

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// הזמנים של המסיבה. נשמר ב-party.json, כדי שהשעון המעורר ישרוד כיבוי.
@Serializable
data class PartyClock(
    @SerialName("התחלה") val startedAt: Long = System.currentTimeMillis(),
    // מתי הסוכן מתעורר מעצמו בפעם הבאה. null = עוד לא נקבע.
    @SerialName("התעוררות הבאה") val nextWakeAt: Long? = null,
    @SerialName("התעוררויות") val wakeCount: Int = 0,
)
