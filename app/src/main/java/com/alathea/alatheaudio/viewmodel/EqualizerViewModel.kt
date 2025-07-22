package com.alathea.alatheaudio.viewmodel

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alathea.alatheaudio.jni.AudioEngineInterface
import com.alathea.alatheaudio.model.EqMode
import com.alathea.alatheaudio.model.EqPreset
import com.alathea.alatheaudio.model.ParametricBand
import com.alathea.alatheaudio.model.PresetData
// Assuming a repository for persisting presets, which would be part of your media-scanner module.
// import com.alathea.alatheaudio.repository.PresetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.log10
import kotlin.math.pow

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val audioEngine: AudioEngineInterface
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

    private val _presets = MutableStateFlow(createDefaultPresets())
    val presets: StateFlow<List<EqPreset>> = _presets.asStateFlow()

    private val _currentPreset = MutableStateFlow<String?>("Flat")
    val currentPreset: StateFlow<String?> = _currentPreset.asStateFlow()

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
    }

    init {
        loadPresetsFromStorage()
        calculateFrequencyResponse()
        applyEqualizerSettings()
    }

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        applyEqualizerSettings()
    }

    fun setEqMode(mode: EqMode) {
        _eqMode.value = mode
        calculateFrequencyResponse()
        applyEqualizerSettings()
    }

    fun updateParametricBand(index: Int, frequency: Float, gain: Float, q: Float) {
        val currentBands = _parametricBands.value.toMutableList()
        if (index in currentBands.indices) {
            currentBands[index] = currentBands[index].copy(
                frequency = frequency,
                gain = gain,
                q = q
            )
            _parametricBands.value = currentBands
            _currentPreset.value = null // Mark as custom
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
            _currentPreset.value = null // Mark as custom
            calculateFrequencyResponse()
            applyEqualizerSettings()
        }
    }

    fun applyPreset(presetName: String) {
        val preset = _presets.value.find { it.name == presetName } ?: return

        when (val data = preset.data) {
            is PresetData.Parametric -> _parametricBands.value = data.bands
            is PresetData.Graphic -> _graphicBands.value = data.gains
        }

        _currentPreset.value = presetName
        calculateFrequencyResponse()
        applyEqualizerSettings()
    }

    fun saveCurrentAsPreset(name: String) {
        if (name.isBlank()) return

        val presetData = when (_eqMode.value) {
            EqMode.PARAMETRIC -> PresetData.Parametric(_parametricBands.value)
            EqMode.GRAPHIC -> PresetData.Graphic(_graphicBands.value)
        }

        val newPreset = EqPreset(name = name, data = presetData)

        val currentPresets = _presets.value.toMutableList()
        val existingIndex = currentPresets.indexOfFirst { it.name == name }

        if (existingIndex >= 0) {
            currentPresets[existingIndex] = newPreset
        } else {
            currentPresets.add(newPreset)
        }

        _presets.value = currentPresets
        _currentPreset.value = name

        savePresetsToStorage()
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
            ParametricBand(id = 1, frequency = 31f, gain = 0f, q = 1.41f),
            ParametricBand(id = 2, frequency = 62f, gain = 0f, q = 1.41f),
            ParametricBand(id = 3, frequency = 125f, gain = 0f, q = 1.41f),
            ParametricBand(id = 4, frequency = 250f, gain = 0f, q = 1.41f),
            ParametricBand(id = 5, frequency = 500f, gain = 0f, q = 1.41f),
            ParametricBand(id = 6, frequency = 1000f, gain = 0f, q = 1.41f),
            ParametricBand(id = 7, frequency = 2000f, gain = 0f, q = 1.41f),
            ParametricBand(id = 8, frequency = 4000f, gain = 0f, q = 1.41f),
            ParametricBand(id = 9, frequency = 8000f, gain = 0f, q = 1.41f),
            ParametricBand(id = 10, frequency = 16000f, gain = 0f, q = 1.41f)
        )
    }

    private fun createDefaultGraphicBands(): List<Float> {
        // 10-band graphic EQ, all gains at 0dB
        return List(10) { 0f }
    }

    private fun createDefaultPresets(): List<EqPreset> {
        return listOf(
            EqPreset(
                name = "Flat",
                data = PresetData.Parametric(createDefaultParametricBands())
            ),
            EqPreset(
                name = "Rock",
                data = PresetData.Graphic(listOf(5f, 3f, -1f, -1f, -1f, 1f, 2f, 6f, 7f, 6f))
            ),
            EqPreset(
                name = "Pop",
                data = PresetData.Graphic(listOf(-1f, 2f, 4f, 5f, 2f, -1f, -2f, -2f, -1f, -1f))
            ),
            EqPreset(
                name = "Jazz",
                data = PresetData.Graphic(listOf(3f, 2f, 1f, 2f, -2f, -2f, 0f, 1f, 2f, 3f))
            ),
            EqPreset(
                name = "Classical",
                data = PresetData.Graphic(listOf(4f, 3f, 2f, -2f, -2f, -2f, 0f, 2f, 3f, 4f))
            ),
            EqPreset(
                name = "Bass Boost",
                data = PresetData.Graphic(listOf(6f, 5f, 4f, 3f, 1f, 0f, 0f, 0f, 0f, 0f))
            ),
            EqPreset(
                name = "Treble Boost",
                data = PresetData.Graphic(listOf(0f, 0f, 0f, 0f, 1f, 3f, 4f, 5f, 6f, 6f))
            ),
            EqPreset(
                name = "Vocal",
                data = PresetData.Graphic(listOf(-2f, -1f, 1f, 3f, 4f, 4f, 3f, 1f, -1f, -2f))
            )
        )
    }

    private fun calculateFrequencyResponse() {
        viewModelScope.launch {
            val points = mutableListOf<Offset>()
            val gainRange = MAX_GAIN - MIN_GAIN

            for (i in 0 until RESPONSE_POINTS) {
                val logFreq = log10(MIN_FREQ_HZ) + (i.toFloat() / (RESPONSE_POINTS - 1)) *
                        (log10(MAX_FREQ_HZ) - log10(MIN_FREQ_HZ))
                val frequency = 10f.pow(logFreq)

                val gain = when (_eqMode.value) {
                    EqMode.PARAMETRIC -> calculateParametricResponse(frequency, _parametricBands.value)
                    EqMode.GRAPHIC -> calculateGraphicResponse(frequency, _graphicBands.value)
                }

                val x = i.toFloat() / (RESPONSE_POINTS - 1)
                val y = 1f - ((gain - MIN_GAIN) / gainRange)

                points.add(Offset(x, y.coerceIn(0f, 1f)))
            }

            _frequencyResponsePoints.value = points
        }
    }

    private fun calculateParametricResponse(frequency: Float, bands: List<ParametricBand>): Float {
        // ⚠️ This is a simplified sum of filter responses. The real response is more complex.
        return bands.sumOf { band ->
            val w0 = 2 * PI.toFloat() * band.frequency
            val alpha = sin(w0) / (2 * band.q) // simplified alpha
            val A = 10f.pow(band.gain / 40f)

            // ⚠️ This is a simplification. A full calculation is complex.
            val ratio = frequency / band.frequency
            val value = 1 + (A * A - 1) / (1 + (band.q * (ratio - 1 / ratio)).pow(2))
            20 * log10(sqrt(value))
        }.toFloat()
    }

    private fun calculateGraphicResponse(frequency: Float, gains: List<Float>): Float {
        val graphicFrequencies = listOf(31.25f, 62.5f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
        if (gains.isEmpty()) return 0f

        val lowerIndex = graphicFrequencies.indexOfLast { it <= frequency }.coerceAtLeast(0)

        if (lowerIndex >= graphicFrequencies.size - 1) {
            return gains.getOrElse(gains.size - 1) { 0f }
        }

        val upperIndex = lowerIndex + 1

        val lowerFreq = graphicFrequencies[lowerIndex]
        val upperFreq = graphicFrequencies[upperIndex]
        val lowerGain = gains.getOrElse(lowerIndex) { 0f }
        val upperGain = gains.getOrElse(upperIndex) { 0f }

        if (upperFreq == lowerFreq) return lowerGain

        val ratio = (log10(frequency) - log10(lowerFreq)) / (log10(upperFreq) - log10(lowerFreq))
        return lowerGain + ratio * (upperGain - lowerGain)
    }

    private fun applyAutoGainCompensation() {
        // ⚠️ This is a simple heuristic. A better implementation might analyze the total power.
        if (!_autoGainCompensation.value) return

        when (_eqMode.value) {
            EqMode.PARAMETRIC -> {
                val positiveGain = _parametricBands.value.filter { it.gain > 0 }.sumOf { it.gain.toDouble() }
                if (positiveGain > 0) {
                    val compensation = - (positiveGain / _parametricBands.value.size).toFloat()
                    _parametricBands.value = _parametricBands.value.map { it.copy(gain = it.gain + compensation) }
                }
            }
            EqMode.GRAPHIC -> {
                val positiveGain = _graphicBands.value.filter { it > 0 }.sumOf { it.toDouble() }
                if (positiveGain > 0) {
                    val compensation = - (positiveGain / _graphicBands.value.size).toFloat()
                    _graphicBands.value = _graphicBands.value.map { it + compensation }
                }
            }
        }
        calculateFrequencyResponse()
    }

    private fun applyEqualizerSettings() {
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

    private fun savePresetsToStorage() {
        viewModelScope.launch {
            presetRepository.saveUserPresets(_presets.value)
            println("INFO: Presets would be saved to storage here.")
        }
    }

    private fun loadPresetsFromStorage() {
        viewModelScope.launch {
            val userPresets = presetRepository.loadUserPresets()
            val allPresets = createDefaultPresets() + userPresets
            _presets.value = allPresets.distinctBy { it.name }
            println("INFO: Presets would be loaded from storage here.")
        }
    }
}
