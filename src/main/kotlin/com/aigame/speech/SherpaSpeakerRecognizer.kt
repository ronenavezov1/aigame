package com.aigame.speech

import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import java.io.ByteArrayInputStream
import java.nio.file.Path
import javax.sound.sampled.AudioSystem

// ============================================================
//  טביעת קול מקומית, בלי ענן: sherpa-onnx עם מודל WeSpeaker
//  (ResNet34). המודל אומן על קולות של אלפי דוברים בהרבה שפות,
//  ולכן מתאים גם לעברית. ההקלטות לא יוצאות מהמחשב.
// ============================================================

class SherpaSpeakerRecognizer(model: Path) : SpeakerRecognizer {

    private val extractor = SpeakerEmbeddingExtractor(
        SpeakerEmbeddingExtractorConfig.builder().setModel(model.toString()).setNumThreads(2).build()
    )

    override val isConfigured = true

    /** synchronized: המודל הוא משאב אחד, וחישוב לוקח שבריר שנייה. */
    @Synchronized
    override fun voiceprint(wav: ByteArray): FloatArray? {
        val (samples, sampleRate) = pcmSamples(wav)
        val stream = extractor.createStream()
        try {
            stream.acceptWaveform(samples, sampleRate)
            stream.inputFinished()
            return if (extractor.isReady(stream)) extractor.compute(stream) else null
        } finally {
            stream.release()
        }
    }
}

/** WAV מונו של 16 ביט → דגימות בין ‎-1 ל-1, וקצב הדגימה. */
internal fun pcmSamples(wav: ByteArray): Pair<FloatArray, Int> =
    AudioSystem.getAudioInputStream(ByteArrayInputStream(wav)).use { input ->
        val format = input.format
        require(format.sampleSizeInBits == 16 && format.channels == 1) { "צריך WAV מונו של 16 ביט, הגיע: $format" }
        val bytes = input.readAllBytes()
        val (low, high) = if (format.isBigEndian) 1 to 0 else 0 to 1
        val samples = FloatArray(bytes.size / 2) { i ->
            ((bytes[2 * i + high].toInt() shl 8) or (bytes[2 * i + low].toInt() and 0xFF)) / 32768f
        }
        samples to format.sampleRate.toInt()
    }
