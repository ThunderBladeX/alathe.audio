package com.alathea.alatheaudio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alathea.alatheaudio.ui.theme.Skin
import com.alathea.alatheaudio.viewmodel.EqualizerViewModel
import kotlin.math.*

@Composable
fun EqualizerView(
    viewModel: EqualizerViewModel,
    skin: Skin,
    modifier: Modifier = Modifier,
    onBandChanged: (Int, Float, Float, Float) -> Unit = { _, _, _, _ -> }
) {
    val eqMode by viewModel.eqMode.collectAsState()
    val isEnabled by viewModel.isEnabled.collectAsState()
    val parametricBands by viewModel.parametricBands.collectAsState()
    val graphicBands by viewModel.graphicBands.collectAsState()
    val presets by viewModel.presets.collectAsState()
    val currentPreset by viewModel.currentPreset.collectAsState()
    val showFrequencyResponse by viewModel.showFrequencyResponse.collectAsState()
    val curveBands = remember(eqMode, parametricBands, graphicBands) {
        if (eqMode == EqMode.PARAMETRIC) {
            parametricBands
        } else {
            graphicBands.mapIndexed { index, gain ->
                ParametricBand(getGraphicEqFrequency(index), gain, 1.41f)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(skin.backgroundColor)
            .padding(16.dp)
    ) {
        EqualizerHeader(
            isEnabled = isEnabled,
            eqMode = eqMode,
            skin = skin,
            onToggleEnabled = { viewModel.setEnabled(it) },
            onModeChanged = { viewModel.setEqMode(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        EqualizerPresets(
            presets = presets,
            currentPreset = currentPreset,
            skin = skin,
            onPresetSelected = { viewModel.applyPreset(it) },
            onSavePreset = { viewModel.saveCurrentAsPreset(it) }
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (showFrequencyResponse) {
            FrequencyResponseCurve(
                bands = curveBands,
                skin = skin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }

        when (eqMode) {
            EqMode.PARAMETRIC -> {
                ParametricEqualizerView(
                    bands = parametricBands,
                    skin = skin,
                    isEnabled = isEnabled,
                    onBandChanged = { index, freq, gain, q ->
                        viewModel.updateParametricBand(index, freq, gain, q)
                        onBandChanged(index, freq, gain, q)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            EqMode.GRAPHIC -> {
                GraphicEqualizerView(
                    bands = graphicBands,
                    skin = skin,
                    isEnabled = isEnabled,
                    onBandChanged = { index, gain ->
                        viewModel.updateGraphicBand(index, gain)
                        onBandChanged(index, getGraphicEqFrequency(index), gain, 1.0f)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        EqualizerAdvancedControls(
            viewModel = viewModel,
            skin = skin
        )
    }
}

@Composable
fun EqualizerHeader(
    isEnabled: Boolean,
    eqMode: EqMode,
    skin: Skin,
    onToggleEnabled: (Boolean) -> Unit,
    onModeChanged: (EqMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggleEnabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = skin.accentColor,
                    checkedTrackColor = skin.accentColor.copy(alpha = 0.5f)
                )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (isEnabled) "ON" else "OFF",
                color = if (isEnabled) skin.primaryTextColor else skin.disabledTextColor,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        SegmentedControl(
            options = listOf("PARAMETRIC", "GRAPHIC"),
            selectedOption = eqMode.name,
            onOptionSelected = { 
                onModeChanged(if (it == "PARAMETRIC") EqMode.PARAMETRIC else EqMode.GRAPHIC)
            },
            skin = skin
        )
    }
}

@Composable
fun ParametricEqualizerView(
    bands: List<ParametricBand>,
    skin: Skin,
    isEnabled: Boolean,
    onBandChanged: (Int, Float, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        items(bands.size) { index ->
            val band = bands[index]
            ParametricBandControl(
                bandIndex = index,
                band = band,
                skin = skin,
                isEnabled = isEnabled,
                onBandChanged = onBandChanged
            )
            if (index < bands.size - 1) {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun ParametricBandControl(
    bandIndex: Int,
    band: ParametricBand,
    skin: Skin,
    isEnabled: Boolean,
    onBandChanged: (Int, Float, Float, Float) -> Unit
) {
    var frequency by remember { mutableFloatStateOf(band.frequency) }
    var gain by remember { mutableFloatStateOf(band.gain) }
    var q by remember { mutableFloatStateOf(band.q) }

    LaunchedEffect(band) {
        frequency = band.frequency
        gain = band.gain
        q = band.q
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = skin.surfaceColor.copy(alpha = if (isEnabled) 1f else 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Band ${bandIndex + 1}",
                color = skin.primaryTextColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Freq:",
                    color = skin.secondaryTextColor,
                    fontSize = 12.sp,
                    modifier = Modifier.width(40.dp)
                )
                Slider(
                    value = log10(frequency),
                    onValueChange = { logFreq ->
                        frequency = 10f.pow(logFreq)
                    },
                    onValueChangeFinished = {
                        onBandChanged(bandIndex, frequency, gain, q)
                    },
                    valueRange = log10(20f)..log10(20000f),
                    enabled = isEnabled,
                    colors = SliderDefaults.colors(
                        thumbColor = skin.accentColor,
                        activeTrackColor = skin.accentColor
                    ),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${frequency.toInt()}Hz",
                    color = skin.primaryTextColor,
                    fontSize = 12.sp,
                    modifier = Modifier.width(60.dp),
                    textAlign = TextAlign.End
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Gain:",
                    color = skin.secondaryTextColor,
                    fontSize = 12.sp,
                    modifier = Modifier.width(40.dp)
                )
                Slider(
                    value = gain,
                    onValueChange = { newGain ->
                        gain = newGain
                    },
                    onValueChangeFinished = {
                        onBandChanged(bandIndex, frequency, gain, q)
                    },
                    valueRange = -15f..15f,
                    enabled = isEnabled,
                    colors = SliderDefaults.colors(
                        thumbColor = skin.accentColor,
                        activeTrackColor = skin.accentColor
                    ),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${gain.format(1)}dB",
                    color = skin.primaryTextColor,
                    fontSize = 12.sp,
                    modifier = Modifier.width(60.dp),
                    textAlign = TextAlign.End
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Q:",
                    color = skin.secondaryTextColor,
                    fontSize = 12.sp,
                    modifier = Modifier.width(40.dp)
                )
                Slider(
                    value = q,
                    onValueChange = { newQ ->
                        q = newQ
                    },
                    onValueChangeFinished = {
                        onBandChanged(bandIndex, frequency, gain, q)
                    },
                    valueRange = 0.1f..10f,
                    enabled = isEnabled,
                    colors = SliderDefaults.colors(
                        thumbColor = skin.accentColor,
                        activeTrackColor = skin.accentColor
                    ),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = q.format(2),
                    color = skin.primaryTextColor,
                    fontSize = 12.sp,
                    modifier = Modifier.width(60.dp),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
fun GraphicEqualizerView(
    bands: List<Float>,
    skin: Skin,
    isEnabled: Boolean,
    onBandChanged: (Int, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val x = change.position.x
                    val y = change.position.y
                    val bandWidth = size.width / bands.size.toFloat()
                    val bandIndex = (x / bandWidth).toInt().coerceIn(0, bands.size - 1)

                    val gain = ((1f - y / size.height) * 30f - 15f).coerceIn(-15f, 15f)
                    onBandChanged(bandIndex, gain)
                }
            }
    ) {
        val textMeasurer = TextMeasurer(this)
        drawGraphicEqualizer(bands, skin, isEnabled)
    }
}

fun DrawScope.drawGraphicEqualizer(
    bands: List<Float>,
    skin: Skin,
    isEnabled: Boolean
    textMeasurer: TextMeasurer
) {
    val bandWidth = size.width / bands.size
    val centerY = size.height / 2
    val maxGain = 15f

    val frequencyLabelStyle = TextStyle(
        color = if (isEnabled) skin.secondaryTextColor else skin.disabledTextColor,
        fontSize = 12.sp,
        textAlign = TextAlign.Center
    )
    val gainLabelStyle = TextStyle(
        color = if (isEnabled) skin.primaryTextColor else skin.disabledTextColor,
        fontSize = 10.sp,
        textAlign = TextAlign.Center
    )

    bands.forEachIndexed { index, gain ->
        val x = bandWidth * (index + 0.5f)
        val frequency = getGraphicEqFrequency(index)
        val label = when {
            frequency < 1000 -> "${frequency.toInt()}"
            else -> "${(frequency / 1000).format(1)}k"
        }

        val textLayoutResult = textMeasurer.measure(
            text = label,
            style = frequencyLabelStyle
        )

        drawText(
            textLayoutResult = textLayoutResult,
            topLeft = Offset(x - textLayoutResult.size.width / 2, size.height - textLayoutResult.size.height - 2.dp.toPx()),
            color = if (isEnabled) skin.secondaryTextColor else skin.disabledTextColor
        )
    }

    bands.forEachIndexed { index, gain ->
        val x = bandWidth * index + bandWidth * 0.1f
        val barWidth = bandWidth * 0.8f
        val barHeight = (gain / maxGain) * (size.height * 0.4f)
        val barTop = if (barHeight >= 0) centerY - barHeight else centerY
        val actualBarHeight = abs(barHeight)
        
        val color = if (isEnabled) {
            if (gain >= 0) skin.accentColor else skin.accentColor.copy(red = 1f, green = 0.3f, blue = 0.3f)
        } else {
            skin.disabledTextColor
        }
        
        drawRoundRect(
            color = color,
            topLeft = Offset(x, barTop),
            size = Size(barWidth, actualBarHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f)
        )

        val gainLabel = "${gain.format(1)}"
        val textLayoutResult = textMeasurer.measure(
            text = gainLabel,
            style = gainLabelStyle
        )

       drawText(
            textLayoutResult = textLayoutResult,
            topLeft = Offset(
                x + barWidth / 2 - textLayoutResult.size.width / 2,
                if (gain >= 0) barTop - textLayoutResult.size.height - 2.dp.toPx() else barTop + actualBarHeight + 2.dp.toPx()
            ),
            color = if (isEnabled) skin.primaryTextColor else skin.disabledTextColor
        )
    }

    drawLine(
        color = skin.secondaryTextColor.copy(alpha = 0.5f),
        start = Offset(0f, centerY),
        end = Offset(size.width, centerY),
        strokeWidth = 2f
    )
}

@Composable
fun FrequencyResponseCurve(
    bands: List<ParametricBand>,
    skin: Skin,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        drawFrequencyResponse(bands, skin)
    }
}

fun DrawScope.drawFrequencyResponse(
    bands: List<ParametricBand>,
    skin: Skin
) {
    val path = Path()
    val points = mutableListOf<Offset>()

    for (i in 0..size.width.toInt() step 2) {
        val frequency = 20f * (20000f / 20f).pow(i / size.width)
        var totalMagnitude = 1.0f
        
        bands.forEach { band ->
            val bandMagnitude = calculateAccurateMagnitude(frequency, band.frequency, band.gain, band.q)
            totalMagnitude *= bandMagnitude
        }

        val totalGainDb = 20f * log10(totalMagnitude.coerceAtLeast(0.0001f))
        val y = size.height / 2 - (totalGainDb / 15f) * (size.height / 2f)
        points.add(Offset(i.toFloat(), y.coerceIn(0f, size.height)))
    }

    if (points.isNotEmpty()) {
        path.moveTo(points[0].x, points[0].y)
        points.forEach { point ->
            path.lineTo(point.x, point.y)
        }
        
        drawPath(
            path = path,
            color = skin.accentColor,
            style = Stroke(width = 3f, cap = StrokeCap.Round)
        )
    }

    for (i in -15..15 step 5) {
        val y = size.height / 2 - (i / 30f) * size.height * 0.4f
        drawLine(
            color = skin.secondaryTextColor.copy(alpha = 0.2f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f
        )
    }
}

@Composable
fun EqualizerPresets(
    presets: List<EqPreset>,
    currentPreset: String?,
    skin: Skin,
    onPresetSelected: (String) -> Unit,
    onSavePreset: (String) -> Unit
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            var expanded by remember { mutableStateOf(false) }
            
            OutlinedButton(
                onClick = { expanded = true },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = skin.primaryTextColor
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(currentPreset ?: "Select Preset")
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Expand")
            }
            
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                presets.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text(preset.name) },
                        onClick = {
                            onPresetSelected(preset.name)
                            expanded = false
                        }
                    )
                }
            }
        }

        IconButton(onClick = { showSaveDialog = true }) {
            Icon(
                Icons.Default.Save,
                contentDescription = "Save Preset",
                tint = skin.accentColor
            )
        }
    }
    
    if (showSaveDialog) {
        SavePresetDialog(
            onSave = { name ->
                onSavePreset(name)
                showSaveDialog = false
            },
            onDismiss = { showSaveDialog = false }
        )
    }
}

@Composable
fun EqualizerAdvancedControls(
    viewModel: EqualizerViewModel,
    skin: Skin
) {
    val showFrequencyResponse by viewModel.showFrequencyResponse.collectAsState()
    val autoGainCompensation by viewModel.autoGainCompensation.collectAsState()
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = showFrequencyResponse,
                onCheckedChange = { viewModel.setShowFrequencyResponse(it) },
                colors = CheckboxDefaults.colors(checkedColor = skin.accentColor)
            )
            Text(
                text = "Show Response",
                color = skin.primaryTextColor,
                fontSize = 12.sp
            )
        }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = autoGainCompensation,
                onCheckedChange = { viewModel.setAutoGainCompensation(it) },
                colors = CheckboxDefaults.colors(checkedColor = skin.accentColor)
            )
            Text(
                text = "Auto Gain",
                color = skin.primaryTextColor,
                fontSize = 12.sp
            )
        }
    }
}

private fun getGraphicEqFrequency(index: Int): Float {
    val frequencies = listOf(32f, 64f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    return frequencies.getOrElse(index) { 1000f }
}

private fun calculateBandResponse(frequency: Float, centerFreq: Float, gain: Float, q: Float): Float {
    val ratio = frequency / centerFreq
    val ratioSquared = ratio * ratio
    val qSquared = q * q
    
    val magnitude = sqrt(
        (1 + gain * qSquared * (ratioSquared - 1) / ratioSquared) /
        (1 + qSquared * (ratioSquared - 1) / ratioSquared)
    )
    
    return 20f * log10(magnitude)
}

private fun Float.format(decimals: Int): String = "%.${decimals}f".format(this)

data class ParametricBand(
    val frequency: Float,
    val gain: Float,
    val q: Float
)

data class EqPreset(
    val name: String,
    val bands: List<Float>
)

enum class EqMode {
    PARAMETRIC,
    GRAPHIC
}
