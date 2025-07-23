package com.alathea.alatheaudio.viewmodel

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alathea.alatheaudio.jni.AudioEngineInterface
import com.alathea.alatheaudio.model.*
import com.alathea.alatheaudio.repository.PresetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import kotlin.math.*

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val audioEngine: AudioEngineInterface,
    private val presetRepository: PresetRepository
) : ViewModel() {

    private val _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _eqMode = MutableStateFlow(EqMode.PARAMETRIC)
    val eqMode: StateFlow<EqMode> = _eqMode.asStateFlow()

    private val _parametricBands = MutableStateFlow(createDefaultParametricBands())
    val parametricBands: StateFlow<List<ParametricBand>> = _parametricBands.asStateFlow()

    private val _graphicBands = MutableStateFlow(createDefaultGraphicBands())
    val graphicBands: StateFlow<List<Float>> = _graphicBands.asStateFlow()

    private val _presets = MutableStateFlow<List<AudioPreset>>(emptyList())
    val presets: StateFlow<List<AudioPreset>> = _presets.asStateFlow()

    private val _currentPresetId = MutableStateFlow<Long?>(null)
    val currentPresetId: StateFlow<Long?> = _currentPresetId.asStateFlow()

    private val _frequencyResponsePoints = MutableStateFlow<List<Offset>>(emptyList())
    val frequencyResponsePoints: StateFlow<List<Offset>> = _frequencyResponsePoints.asStateFlow()

    private val _showFrequencyResponse = MutableStateFlow(true)
    val showFrequencyResponse: StateFlow<Boolean> = _showFrequencyResponse.asStateFlow()

    private val _autoGainCompensation = MutableStateFlow(false)
    val autoGainCompensation: StateFlow<Boolean> = _autoGainCompensation.asStateFlow()

    private companion object {
        const val MIN_FREQ_HZ = 20f
        const val MAX_FREQ_HZ = 20000f
        const val RESPONSE_POINTS = 200
        const val MIN_GAIN = -15f
        const val MAX_GAIN = 15f
        const val SAMPLE_RATE = 48000f
    }

    init {

        presetRepository.allPresets
            .onEach { presets -> _presets.value = presets }
            .launchIn(viewModelScope)

        calculateFrequencyResponse()
        applyEqualizerSettings()
    }

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        applyEqualizerSettings()
    }

    fun setEqMode(mode: EqMode) {
        _eqMode.value = mode
        _currentPresetId.value = null 
        calculateFrequencyResponse()
        applyEqualizerSettings()
    }

    fun updateParametricBand(index: Int, frequency: Float, gain: Float, q: Float) {
        val currentBands = _parametricBands.value.toMutableList()
        if (index in currentBands.indices) {
            currentBands[index] = currentBands[index].copy(frequency = frequency, gain = gain, q = q)
            _parametricBands.value = currentBands
            _currentPresetId.value = null 
            calculateFrequencyResponse()
        }
    }

    fun onParametricBandChangeFinished(index: Int, frequency: Float, gain: Float, q: Float) {
        updateParametricBand(index, frequency, gain, q)
        applyEqualizerSettings()
    }

    fun updateGraphicBand(index: Int, gain: Float) {
        val currentBands = _graphicBands.value.toMutableList()
        if (index in currentBands.indices) {
            currentBands[index] = gain
            _graphicBands.value = currentBands
            _currentPresetId.value = null 
            calculateFrequencyResponse()
            applyEqualizerSettings()
        }
    }

    fun applyPreset(presetId: Long) {
        val preset = _presets.value.find { it.id == presetId } ?: return

        _parametricBands.value = preset.parametricBands
        _graphicBands.value = preset.graphicBands.toList()
        _currentPresetId.value = preset.id

        calculateFrequencyResponse()
        applyEqualizerSettings()
    }

    fun saveCurrentAsPreset(name: String, category: PresetCategory = PresetCategory.USER) {
        if (name.isBlank()) return

        viewModelScope.launch {
            val newPreset = AudioPreset(
                id = 0, 
                name = name,
                description = "User created preset",
                category = category,
                parametricBands = _parametricBands.value,
                graphicBands = _graphicBands.value.toFloatArray(),
                stereoExpansion = 0.0f, 
                replayGainMode = ReplayGainMode.OFF,
                crossfadeDuration = 0,
                dynamicRangeCompression = 0.0f,
                isBuiltIn = false,
                usageCount = 0,
                isFavorite = false,
                createdAt = System.currentTimeMillis(),
                modifiedAt = System.currentTimeMillis()
            )
            val newId = presetRepository.savePreset(newPreset)
            _currentPresetId.value = newId 
        }
    }

    fun deletePreset(presetId: Long) {
        viewModelScope.launch {
            val presetToDelete = presets.value.find { it.id == presetId }
            if (presetToDelete != null && !presetToDelete.isBuiltIn) {
                presetRepository.deletePreset(presetId)
                if (_currentPresetId.value == presetId) {
                    _currentPresetId.value = null
                }
            }
        }
    }

    fun importPreset(file: File) {
        viewModelScope.launch {
            try {

                presetRepository.importPreset(file)

            } catch (e: Exception) {

                e.printStackTrace()
            }
        }
    }

    fun setShowFrequencyResponse(show: Boolean) {
        _showFrequencyResponse.value = show
    }

    fun setAutoGainCompensation(enabled: Boolean) {
        _autoGainCompensation.value = enabled
        if (enabled) {
            applyAutoGainCompensation()
        }
        applyEqualizerSettings()
    }

    private fun createDefaultParametricBands(): List<ParametricBand> {
        return listOf(
            ParametricBand(31f, 0f, 1.41f),
            ParametricBand(62f, 0f, 1.41f),
            ParametricBand(125f, 0f, 1.41f),
            ParametricBand(250f, 0f, 1.41f),
            ParametricBand(500f, 0f, 1.41f),
            ParametricBand(1000f, 0f, 1.41f),
            ParametricBand(2000f, 0f, 1.41f),
            ParametricBand(4000f, 0f, 1.41f),
            ParametricBand(8000f, 0f, 1.41f),
            ParametricBand(16000f, 0f, 1.41f)
        )
    }

    private fun createDefaultGraphicBands(): List<Float> {
        return List(10) { 0f }
    }

    private fun calculateFrequencyResponse() {
        viewModelScope.launch {
            val points = mutableListOf<Offset>()
            val gainRange = MAX_GAIN - MIN_GAIN

            for (i in 0 until RESPONSE_POINTS) {
                val logFreq = log10(MIN_FREQ_HZ) + (i.toFloat() / (RESPONSE_POINTS - 1)) * (log10(MAX_FREQ_HZ) - log10(MIN_FREQ_HZ))
                val frequency = 10f.pow(logFreq)

                val gainDb = when (_eqMode.value) {
                    EqMode.PARAMETRIC -> calculateParametricResponse(frequency, _parametricBands.value)
                    EqMode.GRAPHIC -> calculateGraphicResponse(frequency, _graphicBands.value)
                }

                val x = i.toFloat() / (RESPONSE_POINTS - 1)
                val y = 1f - ((gainDb.coerceIn(MIN_GAIN, MAX_GAIN) - MIN_GAIN) / gainRange)

                points.add(Offset(x, y.coerceIn(0f, 1f)))
            }
            _frequencyResponsePoints.value = points
        }
    }

    private fun calculateParametricResponse(frequency: Float, bands: List<ParametricBand>): Float {

        var totalGain = 0.0
        bands.forEach { band ->
            if (band.gain != 0f) {
                val g = 10.0.pow(band.gain / 20.0)
                val w0 = 2.0 * PI * band.frequency / SAMPLE_RATE
                val alpha = sin(w0) / (2.0 * band.q)

                val a = DoubleArray(3)
                val b = DoubleArray(3)

                b[0] = 1 + alpha * g
                b[1] = -2 * cos(w0)
                b[2] = 1 - alpha * g
                a[0] = 1 + alpha / g
                a[1] = -2 * cos(w0)
                a[2] = 1 - alpha / g

                val phi = (2.0 * PI * frequency / SAMPLE_RATE).pow(2)
                val numerator = (b[0] / a[0]).pow(2) + (b[1] / a[0]).pow(2) - 2 * (b[0] / a[0]) * (b[1] / a[0]) * cos(phi)
                val denominator = 1 + (a[1] / a[0]).pow(2) - 2 * (a[1] / a[0]) * cos(phi)

                val A = 10f.pow(band.gain / 40f) 
                val f_div_f0 = frequency / band.frequency
                val term = ((f_div_f0 * f_div_f0 - 1) / (band.q * band.q))
                val filterGain = band.gain / (1 + term * term).pow(0.5f)
                totalGain += filterGain
            }
        }
        return totalGain.toFloat()
    }

    private fun calculateGraphicResponse(frequency: Float, gains: List<Float>): Float {
        val graphicFrequencies = listOf(31.25f, 62.5f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
        if (gains.isEmpty()) return 0f

        val logFreq = log10(frequency)
        val logGraphicFreqs = graphicFrequencies.map { log10(it) }

        if (logFreq <= logGraphicFreqs.first()) return gains.first()
        if (logFreq >= logGraphicFreqs.last()) return gains.last()

        val upperIndex = logGraphicFreqs.indexOfFirst { it > logFreq }
        val lowerIndex = upperIndex - 1

        val lowerFreq = logGraphicFreqs[lowerIndex]
        val upperFreq = logGraphicFreqs[upperIndex]
        val lowerGain = gains.getOrElse(lowerIndex) { 0f }
        val upperGain = gains.getOrElse(upperIndex) { 0f }

        val ratio = (logFreq - lowerFreq) / (upperFreq - lowerFreq)
        return lowerGain + ratio * (upperGain - lowerGain)
    }

    private fun applyAutoGainCompensation() {
        if (!_autoGainCompensation.value) return

        val compensationDb = when (_eqMode.value) {
            EqMode.PARAMETRIC -> {
                val peakGain = _parametricBands.value.filter { it.gain > 0 }.maxOfOrNull { it.gain } ?: 0f
                -peakGain / 2 
            }
            EqMode.GRAPHIC -> {
                val peakGain = _graphicBands.value.filter { it > 0 }.maxOfOrNull { it } ?: 0f
                -peakGain / 2 
            }
        }

        if (compensationDb == 0f) return

        when(_eqMode.value) {
            EqMode.PARAMETRIC -> _parametricBands.value = _parametricBands.value.map { it.copy(gain = it.gain + compensationDb) }
            EqMode.GRAPHIC -> _graphicBands.value = _graphicBands.value.map { it + compensationDb }
        }

        calculateFrequencyResponse()
    }

    private fun applyEqualizerSettings() {
        viewModelScope.launch {
            val isEqEnabled = _isEnabled.value
            audioEngine.setEqualizerEnabled(isEqEnabled)

            if (isEqEnabled) {
                when (_eqMode.value) {
                    EqMode.PARAMETRIC -> {
                        audioEngine.setParametricBands(_parametricBands.value)
                    }
                    EqMode.GRAPHIC -> {
                        audioEngine.setGraphicBands(_graphicBands.value)
                    }
                }
            }
        }
    }
}
