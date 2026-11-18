package com.aigame.storage

import com.aigame.objects.Voiceprint
import kotlinx.serialization.builtins.ListSerializer
import java.nio.file.Path

// טביעות הקול על הדיסק: data/voices.json. נשמרות בין הפעלות —
// כל שחקן עושה איתחול פעם אחת.
class VoiceprintStorage(dataDir: Path) {

    private val file = FileStore(dataDir.resolve("voices.json"), ListSerializer(Voiceprint.serializer()))

    fun load(): Map<String, List<Float>> = file.read().orEmpty().associate { it.name to it.vector }

    fun save(voiceprints: Map<String, List<Float>>) = file.write(voiceprints.map { (name, vector) -> Voiceprint(name, vector) })
}
