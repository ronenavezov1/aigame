package com.aigame

import com.aigame.speech.GeminiException
import com.aigame.speech.withQuotaFallback
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** נגמרה המכסה היומית של מודל — עוברים לבא. */
class GeminiQuotaTest {

    @Test
    fun `quota is over on one model - the next one answers`() = runTest {
        val tried = mutableListOf<String>()
        val answer = withQuotaFallback(listOf("a", "b", "c")) { model ->
            tried += model
            if (model == "a") throw GeminiException(429, "quota") else "קול מ-$model"
        }
        assertEquals("קול מ-b", answer)
        assertEquals(listOf("a", "b"), tried)
    }

    @Test
    fun `another error stops right away - another model will not fix it`() = runTest {
        val tried = mutableListOf<String>()
        val error = assertFailsWith<GeminiException> {
            withQuotaFallback(listOf("a", "b")) { model ->
                tried += model
                throw GeminiException(400, "bad request")
            }
        }
        assertEquals(400, error.status)
        assertEquals(listOf("a"), tried)
    }

    @Test
    fun `quota is over everywhere - the error comes out`() = runTest {
        val error = assertFailsWith<GeminiException> {
            withQuotaFallback(listOf("a", "b")) { throw GeminiException(429, "quota") }
        }
        assertEquals(429, error.status)
    }
}
