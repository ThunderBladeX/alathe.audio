package com.alathea.alatheaudio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.alathea.alatheaudio.model.EqMode
import com.alathea.alatheaudio.model.EqPreset
import com.alathea.alatheaudio.model.ParametricBand
import com.alathea.alatheaudio.ui.theme.Skin
import com.alathea.alatheaudio.viewmodel.EqualizerViewModel
import kotlin.math.log10
import kotlin.math.pow

private const val MIN_GAIN = -15f
private const val MAX_GAIN = 15f
private const val MIN_FREQ_HZ = 20f
private const val MAX_FREQ_HZ = 20000f

@Composable
fun EqualizerView(
    viewModel: EqualizerViewModel,
    skin: Skin,
    modifier: Modifier = Modifier
) {
    val eqMode by viewModel.eqMode.collectAsState()
    val isEnabled by viewModel.isEnabled.collectAsState()
    val parametricBands by viewModel.parametricBands.collectAsState()
    val graphicBands by viewModel.graphicBands.collectAsState()
    val presets by viewModel.presets.collectAsState()
    val currentPreset by viewModel.currentPreset.collectAsState()
    val frequencyResponsePoints by viewModel.frequencyResponsePoints.collectAsState()
    val showFrequencyResponse by viewModel.showFrequencyResponse.collectAsState()

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
                points = frequencyResponsePoints,
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
                    },
                    onBandChangeFinished = { index, freq, gain, q ->
                        viewModel.onParametricBandChangeFinished(index, freq, gain, q)
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
                    checkedTrackColor = skin.accentColor.copy(alpha = 0.5f),
                    uncheckedThumbColor = skin.secondaryTextColor,
                    uncheckedTrackColor = skin.secondaryTextColor.copy(alpha = 0.3f)
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

        TabRow(
            selectedTabIndex = if (eqMode == EqMode.PARAMETRIC) 0 else 1,
            containerColor = skin.surfaceColor,
            contentColor = skin.accentColor,
            modifier = Modifier.width(200.dp)
        ) {
            Tab(
                selected = eqMode == EqMode.PARAMETRIC,
                onClick = { onModeChanged(EqMode.PARAMETRIC) },
                text = { Text("Parametric") }
            )
            Tab(
                selected = eqMode == EqMode.GRAPHIC,
                onClick = { onModeChanged(EqMode.GRAPHIC) },
                text = { Text("Graphic") }
            )
        }
    }
}

@Composable
fun ParametricEqualizerView(
    bands: List<ParametricBand>,
    skin: Skin,
    isEnabled: Boolean,
    onBandChanged: (Int, Float, Float, Float) -> Unit,
    onBandChangeFinished: (Int, Float, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(bands, key = { _, band -> band.id }) { index, band ->
            ParametricBandControl(
                bandIndex = index,
                band = band,
                skin = skin,
                isEnabled = isEnabled,
                onBandChanged = onBandChanged,
                onBandChangeFinished = onBandChangeFinished
            )
        }
    }
}

@Composable
fun ParametricBandControl(
    bandIndex: Int,
    band: ParametricBand,
    skin: Skin,
    isEnabled: Boolean,
    onBandChangeFinished: (Int, Float, Float, Float) -> Unit,
) {
    var freq by remember { mutableStateOf(band.frequency) }
    var gain by remember { mutableStateOf(band.gain) }
    var q by remember { mutableStateOf(band.q) }

    LaunchedEffect(band) {
        freq = band.frequency
        gain = band.gain
        q = band.q
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = skin.surfaceColor.copy(alpha = if (isEnabled) 1f else 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = "Band ${bandIndex + 1}",
                color = skin.primaryTextColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(4.dp))
            BandSlider(
                label = "Freq",
                value = log10(freq),
                valueText = "${band.frequency.toInt()} Hz",
                valueRange = log10(MIN_FREQ_HZ)..log10(MAX_FREQ_HZ),
                enabled = isEnabled,
                skin = skin,
                onValueChange = { logFreq ->
                    freq = 10f.pow(logFreq)
                },
                onValueChangeFinished = {
                    onBandChangeFinished(bandIndex, freq, gain, q)
                }
            )
            BandSlider(
                label = "Gain",
                value = gain,
                valueText = "${band.gain.format(1)} dB",
                valueRange = MIN_GAIN..MAX_GAIN,
                enabled = isEnabled,
                skin = skin,
                onValueChange = { newGain ->
                    gain = newGain
                },
                onValueChangeFinished = {
                    onBandChangeFinished(bandIndex, freq, gain, q)
                }
            )
            BandSlider(
                label = "Q",
                value = q,
                valueText = q.format(2),
                valueRange = 0.1f..10f,
                enabled = isEnabled,
                skin = skin,
                onValueChange = { newQ ->
                    q = newQ
                },
                onValueChangeFinished = {
                    onBandChangeFinished(bandIndex, freq, gain, q)
                }
            )
        }
    }
}

@Composable
private fun BandSlider(
    label: String,
    value: Float,
    valueText: String,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    skin: Skin,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(32.dp)) {
        Text(
            text = label,
            color = if (enabled) skin.secondaryTextColor else skin.disabledTextColor,
            fontSize = 12.sp,
            modifier = Modifier.width(40.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = skin.accentColor,
                activeTrackColor = skin.accentColor,
                inactiveTrackColor = skin.secondaryTextColor.copy(alpha = 0.3f),
                disabledThumbColor = skin.disabledTextColor,
                disabledActiveTrackColor = skin.disabledTextColor,
            ),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = valueText,
            color = if (enabled) skin.primaryTextColor else skin.disabledTextColor,
            fontSize = 12.sp,
            modifier = Modifier.width(64.dp),
            textAlign = TextAlign.End
        )
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun GraphicEqualizerView(
    bands: List<Float>,
    skin: Skin,
    isEnabled: Boolean,
    onBandChanged: (Int, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    val rememberedOnBandChanged by rememberUpdatedState(onBandChanged)

    val frequencyLabelStyle = remember(skin, isEnabled) {
        TextStyle(
            color = if (isEnabled) skin.secondaryTextColor else skin.disabledTextColor,
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(isEnabled) { 
                if (!isEnabled) return@pointerInput

                detectDragGestures { change, _ ->
                    val x = change.position.x
                    val y = change.position.y
                    val bandWidth = size.width / bands.size.toFloat()
                    val bandIndex = (x / bandWidth).toInt().coerceIn(0, bands.size - 1)

                    val gain = ((1f - (y / size.height)) * (MAX_GAIN - MIN_GAIN) + MIN_GAIN)
                        .coerceIn(MIN_GAIN, MAX_GAIN)

                    rememberedOnBandChanged(bandIndex, gain)
                    change.consume()
                }
            }
    ) {
        drawGraphicEqualizer(bands, skin, isEnabled, textMeasurer, frequencyLabelStyle)
    }
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawGraphicEqualizer(
    bands: List<Float>,
    skin: Skin,
    isEnabled: Boolean,
    textMeasurer: TextMeasurer,
    frequencyLabelStyle: TextStyle
) {
    val bandCount = bands.size
    if (bandCount == 0) return

    val bandWidth = size.width / bandCount
    val centerY = size.height / 2f

    drawLine(
        color = skin.secondaryTextColor.copy(alpha = 0.5f),
        start = Offset(0f, centerY),
        end = Offset(size.width, centerY),
        strokeWidth = 1.dp.toPx()
    )

    bands.forEachIndexed { index, gain ->
        val barCenterX = bandWidth * (index + 0.5f)
        val sliderWidth = bandWidth * 0.6f

        val barTopY = centerY - (gain / MAX_GAIN) * centerY

        val color = if (isEnabled) skin.accentColor else skin.disabledTextColor

        drawLine(
            color = color.copy(alpha = 0.3f),
            start = Offset(barCenterX, 0f),
            end = Offset(barCenterX, size.height),
            strokeWidth = 1.dp.toPx()
        )

        drawCircle(
            color = color,
            radius = sliderWidth / 2,
            center = Offset(barCenterX, barTopY)
        )

        val frequency = getGraphicEqFrequency(index)
        val freqLabel = if (frequency < 1000) "${frequency.toInt()}" else "${(frequency / 1000f).format(1)}k"

        val measuredText = textMeasurer.measure(AnnotatedString(freqLabel), style = frequencyLabelStyle)
        drawText(
            textLayoutResult = measuredText,
            topLeft = Offset(barCenterX - measuredText.size.width / 2, size.height - measuredText.size.height)
        )
    }
}

@Composable
fun FrequencyResponseCurve(
    points: List<Offset>,
    skin: Skin,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))) {

        for (db in listOf(-12, -6, 0, 6, 12)) {

            val y = (size.height / 2f) - (db / MAX_GAIN) * (size.height / 2f)
            drawLine(
                color = skin.secondaryTextColor.copy(alpha = if (db == 0) 0.4f else 0.2f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = if (db != 0) PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f) else null
            )
        }

        if (points.size < 2) return@Canvas

        val path = Path().apply {

            moveTo(points.first().x * size.width, points.first().y * size.height)
            for (i in 1 until points.size) {
                lineTo(points[i].x * size.width, points[i].y * size.height)
            }
        }
        drawPath(
            path = path,
            color = skin.accentColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
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
                colors = ButtonDefaults.outlinedButtonColors(contentColor = skin.primaryTextColor),
                border = BorderStroke(1.dp, skin.secondaryTextColor.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(currentPreset ?: "Custom")
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Expand Presets")
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(skin.surfaceColor)
            ) {
                presets.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text(preset.name, color = skin.primaryTextColor) },
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
            skin = skin,
            onSave = { name ->
                onSavePreset(name)
                showSaveDialog = false
            },
            onDismiss = { showSaveDialog = false }
        )
    }
}

@Composable
fun SavePresetDialog(
    skin: Skin,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = skin.surfaceColor)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Save Preset", style = MaterialTheme.typography.headlineSmall, color = skin.primaryTextColor)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Preset Name") },
                    singleLine = true
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = skin.secondaryTextColor)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onSave(text) }, enabled = text.isNotBlank()) {
                        Text("Save")
                    }
                }
            }
        }
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
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(
                    role = Role.Checkbox,
                    onClick = {
                        viewModel.setShowFrequencyResponse(!showFrequencyResponse)
                    }
                )
                .padding(vertical = 4.dp)
        ) {
            Checkbox(
                checked = showFrequencyResponse,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(checkedColor = skin.accentColor)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Show Curve",
                color = skin.primaryTextColor,
                fontSize = 14.sp
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable {
                    viewModel.setAutoGainCompensation(!autoGainCompensation)
                }
                .padding(vertical = 4.dp)
        ) {
            Checkbox(
                checked = autoGainCompensation,
                onCheckedChange = { viewModel.setAutoGainCompensation(it) },
                colors = CheckboxDefaults.colors(checkedColor = skin.accentColor)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Auto Gain",
                color = skin.primaryTextColor,
                fontSize = 14.sp
            )
        }
    }
}

private fun getGraphicEqFrequency(index: Int): Float {

    val frequencies = listOf(31.25f, 62.5f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    return frequencies.getOrElse(index) { 1000f }
}

private fun Float.format(decimals: Int): String = "%.${decimals}f".format(this)
