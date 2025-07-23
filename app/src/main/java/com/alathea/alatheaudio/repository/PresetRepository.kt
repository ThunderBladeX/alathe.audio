package com.alathea.alatheaudio.repository

import android.content.Context
import android.util.Log
import com.alathea.alatheaudio.model.EqPreset 
import com.alathea.alatheaudio.model.ParametricBand 
import com.alathea.alatheaudio.model.PresetData 
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class PresetRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true 
    }

    private val presetsDir = File(context.filesDir, "eq_presets")
    private val userPresetsFile = File(presetsDir, "user_presets.json")

    @Serializable
    private data class SerializablePreset(
        val name: String,
        val type: String, 
        val parametricBands: List<SerializableParametricBand>? = null,
        val graphicGains: List<Float>? = null
    )

    @Serializable
    private data class SerializableParametricBand(
        val frequency: Float,
        val gain: Float,
        val q: Float
    )

    init {
        ensurePresetsDirectoryExists()
    }

    suspend fun loadUserPresets(): List<EqPreset> = withContext(Dispatchers.IO) {
        if (!userPresetsFile.exists()) return@withContext emptyList()

        try {
            val jsonContent = userPresetsFile.readText()
            val serializedPresets = json.decodeFromString<List<SerializablePreset>>(jsonContent)

            serializedPresets.mapNotNull { serialized ->
                try {
                    when (serialized.type) {
                        "parametric" -> {
                            val bands = serialized.parametricBands?.mapIndexed { index, band ->
                                ParametricBand(
                                    id = index + 1, 
                                    frequency = band.frequency.coerceIn(20f, 20000f),
                                    gain = band.gain.coerceIn(-30f, 30f),
                                    q = band.q.coerceIn(0.1f, 30f)
                                )
                            } ?: emptyList()
                            EqPreset(name = serialized.name, data = PresetData.Parametric(bands))
                        }
                        "graphic" -> {
                            val gains = serialized.graphicGains?.map { gain ->
                                gain.coerceIn(-30f, 30f)
                            } ?: emptyList()
                            EqPreset(name = serialized.name, data = PresetData.Graphic(gains))
                        }
                        else -> {
                            Log.w("PresetRepository", "Skipping preset with unknown type: ${serialized.type}")
                            null
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PresetRepository", "Skipping malformed preset: '${serialized.name}'", e)
                    null 
                }
            }
        } catch (e: Exception) {
            Log.e("PresetRepository", "Failed to load user presets", e)
            emptyList()
        }
    }

    suspend fun saveUserPresets(presets: List<EqPreset>): Boolean = withContext(Dispatchers.IO) {
        try {
            val userPresets = presets.filter { !isDefaultPreset(it.name) }

            val serializedPresets = userPresets.map { preset ->
                when (val data = preset.data) {
                    is PresetData.Parametric -> SerializablePreset(
                        name = preset.name,
                        type = "parametric",
                        parametricBands = data.bands.map { SerializableParametricBand(it.frequency, it.gain, it.q) }
                    )
                    is PresetData.Graphic -> SerializablePreset(
                        name = preset.name,
                        type = "graphic",
                        graphicGains = data.gains
                    )
                }
            }

            val jsonContent = json.encodeToString(serializedPresets)
            userPresetsFile.writeText(jsonContent)
            true
        } catch (e: Exception) {
            Log.e("PresetRepository", "Failed to save user presets", e)
            false
        }
    }

    suspend fun importPreset(file: File): EqPreset = withContext(Dispatchers.IO) {
        try {
            val bands = when (file.extension.lowercase()) {
                "json" -> importJsonPreset(file)
                "feq" -> importFeqPreset(file)
                "peq" -> importPeqPreset(file)
                "txt" -> importGenericTextPreset(file)
                "rew" -> importRewPreset(file)
                else -> throw IllegalArgumentException("Unsupported preset format: .${file.extension}")
            }

            val name = sanitizePresetName(file.nameWithoutExtension)
            EqPreset(name, PresetData.Parametric(bands))

        } catch (e: Exception) {

            throw IOException("Failed to import preset from ${file.name}: ${e.message}", e)
        }
    }

    suspend fun exportPreset(preset: EqPreset, file: File) = withContext(Dispatchers.IO) {
        try {
            when (file.extension.lowercase()) {
                "json" -> exportJsonPreset(preset, file)
                "feq" -> exportFeqPreset(preset, file)
                "txt" -> exportGenericTextPreset(preset, file)
                else -> throw IllegalArgumentException("Unsupported export format: .${file.extension}")
            }
        } catch (e: Exception) {
            throw IOException("Failed to export preset to ${file.name}: ${e.message}", e)
        }
    }

    suspend fun deleteUserPreset(presetName: String) = withContext(Dispatchers.IO) {
        if (isDefaultPreset(presetName)) {
            throw IllegalArgumentException("Cannot delete default preset: $presetName")
        }

        val currentPresets = loadUserPresets().toMutableList()
        val removed = currentPresets.removeAll { it.name == presetName }
        if (removed) {
            saveUserPresets(currentPresets)
        }
    }

    private fun ensurePresetsDirectoryExists() {
        if (!presetsDir.exists()) {
            presetsDir.mkdirs()
        }
    }

    private fun isDefaultPreset(name: String): Boolean {

        return name in setOf("Flat", "Rock", "Pop", "Jazz", "Classical", "Bass Boost", "Treble Boost", "Vocal")
    }

    private fun importJsonPreset(file: File): List<ParametricBand> {
        val serialized = json.decodeFromString<SerializablePreset>(file.readText())
        return when (serialized.type) {
            "parametric" -> serialized.parametricBands?.mapIndexed { i, b ->
                ParametricBand(i + 1, b.frequency, b.gain, b.q)
            } ?: emptyList()
            "graphic" -> convertGraphicToParametricBands(serialized.graphicGains ?: emptyList())
            else -> throw IllegalArgumentException("Unknown preset type in JSON: ${serialized.type}")
        }
    }

    private fun importFeqPreset(file: File): List<ParametricBand> {

        val lines = file.readLines().filter { it.isNotBlank() && it.contains(",") }
        return lines.mapIndexedNotNull { index, line ->
            try {
                val parts = line.split(",").map { it.trim() }
                if (parts.size < 3) return@mapIndexedNotNull null
                ParametricBand(index + 1, parts[0].toFloat(), parts[1].toFloat(), parts[2].toFloat())
            } catch (e: NumberFormatException) { null }
        }
    }

    private fun importPeqPreset(file: File): List<ParametricBand> {

        val lines = file.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        return lines.mapIndexedNotNull { index, line ->
            try {
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size < 3) return@mapIndexedNotNull null
                ParametricBand(index + 1, parts[0].toFloat(), parts[1].toFloat(), parts[2].toFloat())
            } catch (e: NumberFormatException) { null }
        }
    }

    private fun importGenericTextPreset(file: File): List<ParametricBand> {
        val lines = file.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        return lines.mapIndexedNotNull { index, line ->
            try {

                val parts = Regex("-?\\d*\\.?\\d+").findAll(line).map { it.value }.toList()
                if (parts.size < 3) return@mapIndexedNotNull null

                val freq = parts[0].toFloat()
                val gain = parts[1].toFloat()
                val q = parts[2].toFloat()

                if (freq in 20f..20000f && abs(gain) <= 30f && q > 0f) {
                    ParametricBand(index + 1, freq, gain, q)
                } else null
            } catch (e: NumberFormatException) { null }
        }
    }

    private fun importRewPreset(file: File): List<ParametricBand> {
        val lines = file.readLines()
        val dataLines = lines.dropWhile { !it.startsWith("Filter") }.drop(1)

        return dataLines.mapIndexedNotNull { index, line ->
            try {
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size < 9 || parts[1] != "PK") return@mapIndexedNotNull null 

                val freq = parts[3].toFloat()
                val gain = parts[5].toFloat()
                val q = parts[7].toFloat()
                ParametricBand(index + 1, freq, gain, q)
            } catch (e: Exception) { null }
        }
    }

    private fun exportJsonPreset(preset: EqPreset, file: File) {
        val serialized = when (val data = preset.data) {
            is PresetData.Parametric -> SerializablePreset(preset.name, "parametric",
                data.bands.map { SerializableParametricBand(it.frequency, it.gain, it.q) })
            is PresetData.Graphic -> SerializablePreset(preset.name, "graphic", graphicGains = data.gains)
        }
        file.writeText(json.encodeToString(serialized))
    }

    private fun exportFeqPreset(preset: EqPreset, file: File) {
        val bands = getBandsForExport(preset)
        val content = bands.joinToString(separator = "\n") { "${it.frequency}, ${it.gain}, ${it.q}" }
        file.writeText(content)
    }

    private fun exportGenericTextPreset(preset: EqPreset, file: File) {
        val bands = getBandsForExport(preset)
        val content = buildString {
            appendLine("# Preset: ${preset.name}")
            appendLine("# Format: Frequency (Hz), Gain (dB), Q-Factor")
            bands.forEach { appendLine("${it.frequency}, ${it.gain}, ${it.q}") }
        }
        file.writeText(content)
    }

    private fun getBandsForExport(preset: EqPreset): List<ParametricBand> {
        return when (val data = preset.data) {
            is PresetData.Parametric -> data.bands

            is PresetData.Graphic -> convertGraphicToParametricBands(data.gains)
        }
    }

    private fun convertGraphicToParametricBands(gains: List<Float>): List<ParametricBand> {
        val standardFrequencies = listOf(31.25f, 62.5f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
        return gains.take(standardFrequencies.size).mapIndexed { index, gain ->
            ParametricBand(
                id = index + 1,
                frequency = standardFrequencies[index],
                gain = gain,
                q = 1.414f 
            )
        }
    }

    private fun sanitizePresetName(name: String): String {
        return name.trim()
            .replace(Regex("[^a-zA-Z0-9\\s\\-_()\\[\\]]"), "")
            .take(50)
            .ifBlank { "Imported Preset" }
    }
}
