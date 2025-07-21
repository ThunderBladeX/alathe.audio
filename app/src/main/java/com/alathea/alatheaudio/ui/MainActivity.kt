package com.alathea.alatheaudio.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.alathea.alatheaudio.jni.AudioEngineInterface
import com.alathea.alatheaudio.service.PlayerService
import com.alathea.alatheaudio.service.SleepTimer
import com.alathea.alatheaudio.ui.components.*
import com.alathea.alatheaudio.ui.screens.*
import com.alathea.alatheaudio.ui.theme.*
import com.alathea.alatheaudio.viewmodel.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val nowPlayingViewModel: NowPlayingViewModel by viewModels()
    private val libraryViewModel: LibraryViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val skinViewModel: SkinViewModel by viewModels()
    private val equalizerViewModel: EqualizerViewModel by viewModels()
    private val visualizerViewModel: VisualizerViewModel by viewModels()

    private var playerService: PlayerService? = null
    private var isBound = false

    @Inject
    lateinit var audioEngine: AudioEngineInterface

    private lateinit var navController: NavHostController

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            initializeAudioEngine()
            bindPlayerService()
            startMediaScanning()
        } else {
            handlePermissionDenied()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as PlayerService.LocalBinder
            playerService = binder.getService()
            isBound = true
            
            nowPlayingViewModel.setPlayerService(playerService!!)
            visualizerViewModel.setPlayerService(playerService!!)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            playerService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupEdgeToEdgeDisplay()
        requestPermissions()
        setContent {
            val currentSkin by skinViewModel.currentSkin.collectAsStateWithLifecycle()
            val isDarkTheme by settingsViewModel.isDarkTheme.collectAsStateWithLifecycle()
            
            AudioPlayerTheme(
                skin = currentSkin,
                darkTheme = isDarkTheme
            ) {
                navController = rememberNavController()
                AudioPlayerApp(
                    navController = navController,
                    nowPlayingViewModel = nowPlayingViewModel,
                    libraryViewModel = libraryViewModel,
                    settingsViewModel = settingsViewModel,
                    skinViewModel = skinViewModel,
                    equalizerViewModel = equalizerViewModel,
                    visualizerViewModel = visualizerViewModel,
                    audioEngine = audioEngine
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!isBound) {
            bindPlayerService()
        }
    }
    override fun onStop() {
        super.onStop()
    }
    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        audioEngine.cleanup()
    }

    private fun setupEdgeToEdgeDisplay() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
        settingsViewModel.keepScreenOn.observe(this) { keepOn ->
            if (keepOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.INTERNET)
            add(Manifest.permission.ACCESS_NETWORK_STATE)
        }

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            initializeAudioEngine()
            bindPlayerService()
            startMediaScanning()
        }
    }

    private fun handlePermissionDenied() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("This app needs access to your storage to play music from your device. Without this permission, some features may not work properly.")
            .setCancelable(false)
            .setPositiveButton("Grant Permission") { _, _ ->
                requestPermissions()
            }
            .setNegativeButton("Continue in Limited Mode") { _, _ ->
                Toast.makeText(this, "Limited mode enabled.", Toast.LENGTH_SHORT).show()
                enterLimitedMode()
            }
            .show()
    }

    private fun enterLimitedMode() {
        // ⚠️ Disable the file picker or local playback
        // ⚠️ Maybe switch to streaming only, or just UI with dummy data
    }

    private fun bindPlayerService() {
        val intent = Intent(this, PlayerService::class.java)
        startForegroundService(intent)
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)
    }

    private fun initializeAudioEngine() {
        audioEngine.initialize(
            sampleRate = settingsViewModel.preferredSampleRate.value,
            bufferSize = settingsViewModel.bufferSize.value,
            enableHighResOutput = settingsViewModel.highResOutput.value,
            enableDirectDacOutput = settingsViewModel.directDacOutput.value
        )
        setupDspChain()
    }

    private fun setupDspChain() {
        val settings = settingsViewModel

        audioEngine.setParametricEqEnabled(settings.parametricEqEnabled.value)
        audioEngine.setGraphicEqEnabled(settings.graphicEqEnabled.value)
        audioEngine.setStereoExpansionEnabled(settings.stereoExpansionEnabled.value)
        audioEngine.setReplayGainEnabled(settings.replayGainEnabled.value)
        audioEngine.setCrossfadeEnabled(settings.crossfadeEnabled.value)
        audioEngine.setCrossfadeDuration(settings.crossfadeDuration.value)

        equalizerViewModel.parametricBands.value.forEachIndexed { index, band ->
            audioEngine.setParametricBand(index, band.frequency, band.gain, band.q)
        }
        equalizerViewModel.graphicBands.value.forEachIndexed { index, gain ->
            audioEngine.setGraphicBand(index, gain)
        }
    }

    private fun startMediaScanning() {
        libraryViewModel.startMediaScan()
    }
}

@Composable
fun AudioPlayerApp(
    navController: NavHostController,
    nowPlayingViewModel: NowPlayingViewModel,
    libraryViewModel: LibraryViewModel,
    settingsViewModel: SettingsViewModel,
    skinViewModel: SkinViewModel,
    equalizerViewModel: EqualizerViewModel,
    visualizerViewModel: VisualizerViewModel,
    audioEngine: AudioEngineInterface
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val currentTrack by nowPlayingViewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by nowPlayingViewModel.isPlaying.collectAsStateWithLifecycle()
    val playbackPosition by nowPlayingViewModel.playbackPosition.collectAsStateWithLifecycle()
    val currentSkin by skinViewModel.currentSkin.collectAsStateWithLifecycle()
    val showMiniPlayer by settingsViewModel.showMiniPlayer.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        NavHost(
            navController = navController,
            startDestination = "library",
            modifier = Modifier.padding(
                bottom = if (showMiniPlayer && currentTrack != null) 72.dp else 0.dp
            )
        ) {
            composable("library") {
                LibraryScreen(
                    viewModel = libraryViewModel,
                    onTrackClick = { track ->
                        nowPlayingViewModel.playTrack(track)
                    },
                    onNavigateToNowPlaying = {
                        navController.navigate("nowplaying")
                    }
                )
            }
            
            composable("nowplaying") {
                NowPlayingScreen(
                    nowPlayingViewModel = nowPlayingViewModel,
                    equalizerViewModel = equalizerViewModel,
                    visualizerViewModel = visualizerViewModel,
                    settingsViewModel = settingsViewModel,
                    skinViewModel = skinViewModel,
                    audioEngine = audioEngine,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
            
            composable("settings") {
                SettingsScreen(
                    settingsViewModel = settingsViewModel,
                    skinViewModel = skinViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
            
            composable("equalizer") {
                EqualizerScreen(
                    equalizerViewModel = equalizerViewModel,
                    audioEngine = audioEngine,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
            
            composable("playlist/{playlistId}") { backStackEntry ->
                val playlistId = backStackEntry.arguments?.getString("playlistId")?.toLongOrNull()
                if (playlistId != null) {
                    PlaylistScreen(
                        playlistId = playlistId,
                        libraryViewModel = libraryViewModel,
                        nowPlayingViewModel = nowPlayingViewModel,
                        onNavigateBack = {
                            navController.popBackStack()
                        }
                    )
                }
            }
        }

        if (showMiniPlayer && currentTrack != null) {
            MiniPlayer(
                track = currentTrack,
                isPlaying = isPlaying,
                position = playbackPosition,
                skin = currentSkin,
                onPlayPause = { nowPlayingViewModel.togglePlayPause() },
                onNext = { nowPlayingViewModel.skipToNext() },
                onPrevious = { nowPlayingViewModel.skipToPrevious() },
                onClick = {
                    navController.navigate("nowplaying")
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            )
        }

        val sleepTimerRemaining by nowPlayingViewModel.sleepTimerRemaining.collectAsStateWithLifecycle()
        if (sleepTimerRemaining > 0) {
            SleepTimerIndicator(
                timeRemaining = sleepTimerRemaining,
                onCancel = { nowPlayingViewModel.cancelSleepTimer() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            )
        }
    }
}

@Composable
fun MiniPlayer(
    track: Track?,
    isPlaying: Boolean,
    position: Long,
    skin: Skin,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (track == null) return
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = skin.miniPlayerBackground
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArt(
                albumArtUri = track.albumArtUri,
                size = 48.dp,
                cornerRadius = 6.dp
            )
            
            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = skin.primaryTextColor,
                    maxLines = 1
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = skin.secondaryTextColor,
                    maxLines = 1
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = skin.controlColor
                    )
                }
                IconButton(onClick = onPlayPause) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = skin.controlColor
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = skin.controlColor
                    )
                }
            }
        }

        LinearProgressIndicator(
            progress = if (track.duration > 0) position.toFloat() / track.duration else 0f,
            modifier = Modifier.fillMaxWidth(),
            color = skin.accentColor,
            trackColor = skin.progressTrackColor
        )
    }
}

@Composable
fun SleepTimerIndicator(
    timeRemaining: Long,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = "Sleep Timer",
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatTime(timeRemaining),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val seconds = (milliseconds / 1000) % 60
    val minutes = (milliseconds / (1000 * 60)) % 60
    val hours = (milliseconds / (1000 * 60 * 60))
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
