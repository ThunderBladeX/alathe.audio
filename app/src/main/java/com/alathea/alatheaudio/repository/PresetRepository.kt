package com.alathea.alatheaudio.repository

import android.content.Context
import androidx.room.Room
import com.alathea.alatheaudio.database.PresetDatabase
import com.alathea.alatheaudio.database.dao.PresetDao
import com.alathea.alatheaudio.database.entity.PresetEntity
import com.alathea.alatheaudio.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PresetRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val presetDao: PresetDao,
    private val json: Json
) {
    companion object {
        private const val PRESETS_DIR = "presets"
        private const val BACKUP_DIR = "preset_backups"
        private const val DEFAULT_PRESETS_FILE = "default_presets.json"

        private val BUILT_IN_PRESETS = listOf(
            BuiltInPreset(
                id = -1L,
                name = "Classical",
                description = "Enhanced clarity for orchestral music",
                category = PresetCategory.GENRE,
                parametricBands = listOf(
                    ParametricBand(32f, 2.0f, 0.7f),    // Sub-bass roll-off
                    ParametricBand(60f, 1.5f, 0.8f),    // Bass clarity
                    ParametricBand(200f, -1.0f, 1.2f),  // Warmth reduction
                    ParametricBand(1000f, 0.5f, 1.0f),  // Midrange presence
                    ParametricBand(3000f, 1.0f, 0.9f),  // Vocal clarity
                    ParametricBand(8000f, 2.0f, 0.8f),  // String brightness
                    ParametricBand(16000f, 1.5f, 0.7f)  # Air and sparkle
                ),
                graphicBands = floatArrayOf(1.5f, 1.0f, 0.0f, 0.5f, 1.0f, 2.0f, 1.5f, 1.0f, 0.5f, 0.0f),
                stereoExpansion = 0.3f,
                replayGainMode = ReplayGainMode.ALBUM,
                crossfadeDuration = 2000
            ),

            BuiltInPreset(
                id = -2L,
                name = "Electronic",
                description = "Punchy bass and crisp highs for electronic music",
                category = PresetCategory.GENRE,
                parametricBands = listOf(
                    ParametricBand(40f, 4.0f, 0.8f),    // Sub-bass boost
                    ParametricBand(80f, 3.0f, 1.0f),    // Bass punch
                    ParametricBand(200f, 1.0f, 0.9f),   // Body
                    ParametricBand(800f, -1.5f, 1.1f),  // Midrange scoop
                    ParametricBand(2000f, -0.5f, 0.8f), // Vocal clarity
                    ParametricBand(6000f, 2.5f, 0.9f),  # Synth brightness
                    ParametricBand(12000f, 3.0f, 0.7f)  # Air and detail
                ),
                graphicBands = floatArrayOf(4.0f, 3.0f, 1.0f, -1.0f, -0.5f, 1.0f, 2.5f, 3.0f, 2.0f, 1.0f),
                stereoExpansion = 0.6f,
                replayGainMode = ReplayGainMode.TRACK,
                crossfadeDuration = 4000
            ),

            BuiltInPreset(
                id = -3L,
                name = "Rock",
                description = "Powerful mids and controlled bass for rock music",
                category = PresetCategory.GENRE, 
                parametricBands = listOf(
                    ParametricBand(60f, 2.0f, 0.9f),    // Bass foundation
                    ParametricBand(120f, 1.0f, 1.0f),   // Kick drum punch
                    ParametricBand(400f, 2.5f, 1.2f),   // Guitar body
                    ParametricBand(1200f, 3.0f, 1.1f),  // Guitar presence
                    ParametricBand(2500f, 1.5f, 0.8f),  // Vocal cut
                    ParametricBand(5000f, 2.0f, 0.9f),  // Cymbal attack
                    ParametricBand(10000f, 1.0f, 0.8f)  # Guitar harmonics
                ),
                graphicBands = floatArrayOf(2.0f, 1.5f, 2.5f, 3.0f, 1.5f, 2.0f, 1.5f, 1.0f, 0.5f, 0.0f),
                stereoExpansion = 0.4f,
                replayGainMode = ReplayGainMode.ALBUM,
                crossfadeDuration = 1500
            ),

            BuiltInPreset(
                id = -4L,
                name = "Jazz",
                description = "Natural midrange for acoustic instruments",
                category = PresetCategory.GENRE,
                parametricBands = listOf(
                    ParametricBand(50f, 1.0f, 0.8f),    // Upright bass
                    ParametricBand(100f, 0.5f, 1.0f),   // Bass warmth
                    ParametricBand(300f, 1.5f, 1.1f),   // Piano body
                    ParametricBand(1000f, 2.0f, 0.9f),  // Vocal/horn presence
                    ParametricBand(3000f, 1.0f, 0.8f),  // Brass brightness
                    ParametricBand(7000f, 1.5f, 0.7f),  // Cymbal detail
                    ParametricBand(15000f, 0.5f, 0.6f)  # Natural air
                ),
                graphicBands = floatArrayOf(1.0f, 0.5f, 1.5f, 2.0f, 1.0f, 1.5f, 1.0f, 0.5f, 0.0f, -0.5f),
                stereoExpansion = 0.2f,
                replayGainMode = ReplayGainMode.ALBUM,
                crossfadeDuration = 3000
            ),

            BuiltInPreset(
                id = -5L,
                name = "Vocal Focus",
                description = "Enhanced vocal clarity and presence",
                category = PresetCategory.PURPOSE,
                parametricBands = listOf(
                    ParametricBand(80f, -1.0f, 0.8f),   // Reduce muddiness
                    ParametricBand(200f, -1.5f, 1.0f),  // Proximity effect
                    ParametricBand(800f, 1.0f, 1.2f),   // Vocal warmth
                    ParametricBand(2000f, 3.0f, 1.0f),  // Vocal presence
                    ParametricBand(4000f, 2.0f, 0.8f),  // Consonant clarity
                    ParametricBand(8000f, 1.0f, 0.7f),  // Sibilance control
                    ParametricBand(12000f, 0.5f, 0.6f)  # Natural brightness
                ),
                graphicBands = floatArrayOf(-1.0f, -1.5f, 0.0f, 1.0f, 3.0f, 2.0f, 1.0f, 0.5f, 0.0f, 0.0f),
                stereoExpansion = 0.1f,
                replayGainMode = ReplayGainMode.TRACK,
                crossfadeDuration = 2000
            ),

            BuiltInPreset(
                id = -6L,
                name = "Headphone Correction",
                description = "Compensates for typical headphone response",
                category = PresetCategory.CORRECTION,
                parametricBands = listOf(
                    ParametricBand(30f, 2.0f, 0.7f),    // Sub-bass extension
                    ParametricBand(80f, 1.0f, 0.9f),    // Bass presence
                    ParametricBand(200f, -0.5f, 1.0f),  // Lower mid clarity
                    ParametricBand(1000f, 0.0f, 1.0f),  // Reference point
                    ParametricBand(3000f, -2.0f, 1.2f), // Harman curve dip
                    ParametricBand(6000f, -1.0f, 0.8f), // Reduce harshness
                    ParametricBand(10000f, 1.5f, 0.7f)  # Restore air
                ),
                graphicBands = floatArrayOf(2.0f, 1.0f, -0.5f, 0.0f, -2.0f, -1.0f, 0.5f, 1.5f, 1.0f, 0.5f),
                stereoExpansion = 0.0f,
                replayGainMode = ReplayGainMode.TRACK,
                crossfadeDuration = 1000
            ),

            BuiltInPreset(
                id = -7L,
                name = "Late Night",
                description = "Reduced dynamics for quiet listening",
                category = PresetCategory.PURPOSE,
                parametricBands = listOf(
                    ParametricBand(40f, -2.0f, 0.8f),   // Reduce bass bleed
                    ParametricBand(100f, -1.0f, 1.0f),  // Controlled low end
                    ParametricBand(500f, 1.0f, 1.1f),   // Midrange focus
                    ParametricBand(1500f, 2.0f, 0.9f),  // Vocal clarity
                    ParametricBand(4000f, 1.5f, 0.8f),  // Detail enhancement
                    ParametricBand(8000f, -0.5f, 0.7f), // Reduce fatigue
                    ParametricBand(16000f, -1.0f, 0.6f) # Gentle top end
                ),
                graphicBands = floatArrayOf(-2.0f, -1.0f, 0.5f, 1.0f, 2.0f, 1.5f, 0.0f, -0.5f, -1.0f, -1.5f),
                stereoExpansion = 0.0f,
                replayGainMode = ReplayGainMode.ALBUM,
                crossfadeDuration = 3000,
                dynamicRangeCompression = 0.3f
            )
        )
    }

    val allPresets: Flow<List<AudioPreset>> = presetDao.getAllPresets().map { entities ->
        val userPresets = entities.map { entity -> entityToPreset(entity) }
        val builtInPresets = BUILT_IN_PRESETS.map { builtIn -> builtInToPreset(builtIn) }
        builtInPresets + userPresets
    }

    fun getPresetsByCategory(category: PresetCategory): Flow<List<AudioPreset>> {
        return allPresets.map { presets ->
            presets.filter { it.category == category }
        }
    }

    val userPresets: Flow<List<AudioPreset>> = presetDao.getAllPresets().map { entities ->
        entities.map { entity -> entityToPreset(entity) }
    }
    
    suspend fun getPresetById(id: Long): AudioPreset? = withContext(Dispatchers.IO) {
        BUILT_IN_PRESETS.find { it.name.hashCode().toLong() == id }?.let { builtIn ->
            return@withContext builtInToPreset(builtIn)
        }

        presetDao.getPresetById(id)?.let { entity ->
            return@withContext entityToPreset(entity)
        }
        null
    }
    
    suspend fun savePreset(preset: AudioPreset): Long = withContext(Dispatchers.IO) {
        val entity = presetToEntity(preset)
        presetDao.insertPreset(entity)
    }
    
    suspend fun updatePreset(preset: AudioPreset) = withContext(Dispatchers.IO) {
        val entity = presetToEntity(preset)
        presetDao.updatePreset(entity)
    }
    
    suspend fun deletePreset(presetId: Long) = withContext(Dispatchers.IO) {
        presetDao.deletePreset(presetId)
    }
    
    suspend fun duplicatePreset(presetId: Long, newName: String): Long = withContext(Dispatchers.IO) {
        val originalPreset = getPresetById(presetId) ?: throw IllegalArgumentException("Preset not found")
        val duplicatedPreset = originalPreset.copy(
            id = 0,
            name = newName,
            isBuiltIn = false,
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis()
        )
        savePreset(duplicatedPreset)
    }

    suspend fun exportPreset(preset: AudioPreset, file: File) = withContext(Dispatchers.IO) {
        val exportData = PresetExportData(
            version = 1,
            preset = preset,
            exportedAt = System.currentTimeMillis(),
            appVersion = getAppVersion()
        )
        
        FileOutputStream(file).use { fos ->
            fos.write(json.encodeToString(exportData).toByteArray())
        }
    }

    suspend fun importPreset(file: File): AudioPreset = withContext(Dispatchers.IO) {
        val jsonString = FileInputStream(file).use { fis ->
            fis.readBytes().decodeToString()
        }
        
        val exportData = json.decodeFromString<PresetExportData>(jsonString)
        val importedPreset = exportData.preset.copy(
            id = 0,
            isBuiltIn = false,
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis()
        )
        
        val newId = savePreset(importedPreset)
        importedPreset.copy(id = newId)
    }

    suspend fun exportPresetsToDirectory(presets: List<AudioPreset>, directory: File) = withContext(Dispatchers.IO) {
        if (!directory.exists()) {
            directory.mkdirs()
        }
        
        presets.forEach { preset ->
            val fileName = "${preset.name.replace(Regex("[^a-zA-Z0-9\\-_]"), "_")}.preset"
            val file = File(directory, fileName)
            exportPreset(preset, file)
        }
    }

    suspend fun importPresetsFromDirectory(directory: File): List<AudioPreset> = withContext(Dispatchers.IO) {
        val importedPresets = mutableListOf<AudioPreset>()
        
        directory.listFiles { _, name -> name.endsWith(".preset") }?.forEach { file ->
            try {
                val preset = importPreset(file)
                importedPresets.add(preset)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        importedPresets
    }

    suspend fun searchPresets(query: String): List<AudioPreset> = withContext(Dispatchers.IO) {
        val userResults = presetDao.searchPresets("%$query%").map { entityToPreset(it) }
        val builtInResults = BUILT_IN_PRESETS
            .filter { 
                it.name.contains(query, ignoreCase = true) || 
                it.description.contains(query, ignoreCase = true) 
            }
            .map { builtInToPreset(it) }
        
        builtInResults + userResults
    }

    suspend fun createBackup(): File = withContext(Dispatchers.IO) {
        val backupDir = File(context.filesDir, BACKUP_DIR)
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }
        
        val timestamp = System.currentTimeMillis()
        val backupFile = File(backupDir, "presets_backup_$timestamp.json")
        
        val userPresetsList = presetDao.getAllPresetsSync()
        val backupData = PresetBackupData(
            version = 1,
            createdAt = timestamp,
            appVersion = getAppVersion(),
            presets = userPresetsList.map { entityToPreset(it) }
        )
        
        FileOutputStream(backupFile).use { fos ->
            fos.write(json.encodeToString(backupData).toByteArray())
        }
        backupFile
    }

    suspend fun restoreFromBackup(backupFile: File, replaceExisting: Boolean = false) = withContext(Dispatchers.IO) {
        val jsonString = FileInputStream(backupFile).use { fis ->
            fis.readBytes().decodeToString()
        }
        
        val backupData = json.decodeFromString<PresetBackupData>(jsonString)
        
        if (replaceExisting) {
            presetDao.deleteAllPresets()
        }
        
        backupData.presets.forEach { preset ->
            val presetToSave = preset.copy(
                id = 0, // Reset ID
                createdAt = System.currentTimeMillis(),
                modifiedAt = System.currentTimeMillis()
            )
            savePreset(presetToSave)
        }
    }

    suspend fun getPresetUsageStats(): Map<Long, Int> = withContext(Dispatchers.IO) {
        presetDao.getPresetUsageStats()
    }

    suspend fun incrementPresetUsage(presetId: Long) = withContext(Dispatchers.IO) {
        presetDao.incrementUsageCount(presetId)
    }

    suspend fun initializeBuiltInPresets() = withContext(Dispatchers.IO) {
        val prefsFile = File(context.filesDir, DEFAULT_PRESETS_FILE)
        if (!prefsFile.exists()) {
            // First run - create marker file
            prefsFile.createNewFile()
        }
    }
    
    private fun presetToEntity(preset: AudioPreset): PresetEntity {
        return PresetEntity(
            id = preset.id,
            name = preset.name,
            description = preset.description,
            category = preset.category.name,
            parametricBandsJson = json.encodeToString(preset.parametricBands),
            graphicBandsJson = json.encodeToString(preset.graphicBands.toList()),
            stereoExpansion = preset.stereoExpansion,
            replayGainMode = preset.replayGainMode.name,
            crossfadeDuration = preset.crossfadeDuration,
            dynamicRangeCompression = preset.dynamicRangeCompression,
            usageCount = preset.usageCount,
            isFavorite = preset.isFavorite,
            createdAt = preset.createdAt,
            modifiedAt = preset.modifiedAt
        )
    }
    
    private fun entityToPreset(entity: PresetEntity): AudioPreset {
        return AudioPreset(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            category = PresetCategory.valueOf(entity.category),
            parametricBands = json.decodeFromString(entity.parametricBandsJson),
            graphicBands = json.decodeFromString<List<Float>>(entity.graphicBandsJson).toFloatArray(),
            stereoExpansion = entity.stereoExpansion,
            replayGainMode = ReplayGainMode.valueOf(entity.replayGainMode),
            crossfadeDuration = entity.crossfadeDuration,
            dynamicRangeCompression = entity.dynamicRangeCompression,
            isBuiltIn = false,
            usageCount = entity.usageCount,
            isFavorite = entity.isFavorite,
            createdAt = entity.createdAt,
            modifiedAt = entity.modifiedAt
        )
    }
    
    private fun builtInToPreset(builtIn: BuiltInPreset): AudioPreset {
        return AudioPreset(
            id = builtIn.id,
            name = builtIn.name,
            description = builtIn.description,
            category = builtIn.category,
            parametricBands = builtIn.parametricBands,
            graphicBands = builtIn.graphicBands,
            stereoExpansion = builtIn.stereoExpansion,
            replayGainMode = builtIn.replayGainMode,
            crossfadeDuration = builtIn.crossfadeDuration,
            dynamicRangeCompression = builtIn.dynamicRangeCompression ?: 0.0f,
            isBuiltIn = true,
            usageCount = 0,
            isFavorite = false,
            createdAt = 0L,
            modifiedAt = 0L
        )
    }
    
    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName
        } catch (e: Exception) {
            "unknown"
        }
    }
}

@kotlinx.serialization.Serializable
data class PresetExportData(
    val version: Int,
    val preset: AudioPreset,
    val exportedAt: Long,
    val appVersion: String
)

@kotlinx.serialization.Serializable
data class PresetBackupData(
    val version: Int,
    val createdAt: Long,
    val appVersion: String,
    val presets: List<AudioPreset>
)

@kotlinx.serialization.Serializable
data class BuiltInPreset(
    val name: String,
    val description: String,
    val category: PresetCategory,
    val parametricBands: List<ParametricBand>,
    val graphicBands: FloatArray,
    val stereoExpansion: Float = 0.0f,
    val replayGainMode: ReplayGainMode = ReplayGainMode.OFF,
    val crossfadeDuration: Int = 2000,
    val dynamicRangeCompression: Float? = null
)
