package com.aigame.orchestrator

import com.aiagent.lib.llm.LlmClient

/**
 * כל ה-LLM-ים שזמינים לסוכנים. המנצח בוחר מכאן לכל סוכן שהוא בונה —
 * איזה מודל, ובאיזו דרגה (FAST / SMART).
 *
 * null = לא מוגדר (אין מפתח).
 */
data class Llms(
    /** Claude: FAST = Sonnet 5.5, SMART = Opus 5.5. */
    val claude: LlmClient? = null,
    /** Gemini (המפתח מ-AI Studio): FAST = Flash, SMART = Pro. */
    val gemini: LlmClient? = null,
    /** המודל המקומי (Ollama). רק FAST. */
    val local: LlmClient? = null,
)
