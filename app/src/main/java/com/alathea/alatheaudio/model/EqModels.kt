package com.alathea.alatheaudio.model

data class ParametricBand(
    val id: Int,
    val frequency: Float,
    val gain: Float,
    val q: Float
)

sealed class PresetData {
    data class Graphic(val gains: List<Float>) : PresetData()
    data class Parametric(val bands: List<ParametricBand>) : PresetData()
}

data class EqPreset(
    val name: String,
    val data: PresetData
)

enum class EqMode {
    PARAMETRIC,
    GRAPHIC
}
