package com.alathea.alatheaudio.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alathea.alatheaudio.ui.theme.Skin
import com.alathea.alatheaudio.ui.components.TrackListConfiguration
import com.alathea.mediascanner.entity.TrackEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.snapshotFlow

@Composable
fun rememberDragDropState(
    lazyListState: LazyListState,
    onMove: (Int, Int) -> Unit,
    scope: CoroutineScope = rememberCoroutineScope()
): DragDropState {
    return remember { DragDropState(lazyListState, onMove) }
}

class DragDropState internal constructor(
    private val lazyListState: LazyListState,
    private val onMove: (Int, Int) -> Unit,
    internal val scope: CoroutineScope
) {
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    internal var draggingItemOffset by mutableFloatStateOf(0f)
        private set

    internal var initialDraggingItemOffset by mutableStateOf<Offset?>(null)
        private set

    internal val scope = CoroutineScope(Job())

    internal fun onDragStart(offset: Offset, index: Int) {
        draggingItemIndex = index
        initialDraggingItemOffset = offset
    }

    internal fun onDragInterrupted() {
        draggingItemIndex = null
        draggingItemOffset = 0f
        initialDraggingItemOffset = null
    }

    internal fun onDrag(offset: Offset) {
        draggingItemOffset += offset.y
        
        val initialOffset = initialDraggingItemOffset ?: return
        val startOffset = initialOffset.y + draggingItemOffset
        val endOffset = startOffset + (lazyListState.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.height ?: 0)
        val currentIndexOfDraggedItem = draggingItemIndex ?: return

        lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { item ->
                val itemCenterY = item.offset.y + item.size.height / 2
                item.index != currentIndexOfDraggedItem && startOffset < itemCenterY && endOffset > itemCenterY
            }?.also { itemOver ->
                onMove(currentIndexOfDraggedItem, itemOver.index)
                draggingItemIndex = itemOver.index
            }
    }
}

fun Modifier.draggableItem(
    state: DragDropState,
    index: Int
): Modifier = this
    .graphicsLayer {
        val isDragging = index == state.draggingItemIndex
        translationY = if (isDragging) state.draggingItemOffset else 0f
        scaleX = if (isDragging) 1.05f else 1f
        scaleY = if (isDragging) 1.05f else 1f
        shadowElevation = if (isDragging) 8f else 0f
    }
    .pointerInput(Unit) {
        detectDragGesturesAfterLongPress(
            onDragStart = { offset -> state.onDragStart(offset, index) },
            onDragEnd = { state.onDragInterrupted() },
            onDragCancel = { state.onDragInterrupted() },
            onDrag = { change, dragAmount ->
                change.consume()
                state.onDrag(dragAmount)
            }
        )
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackListView(
    tracks: List<TrackEntity>,
    currentTrack: TrackEntity?,
    isPlaying: Boolean,
    skin: Skin,
    isReorderable: Boolean = false,
    showTrackNumbers: Boolean = true,
    showDurations: Boolean = true,
    showAlbumArt: Boolean = true,
    enableSwipeActions: Boolean = true,
    enableMultiSelect: Boolean = false,
    config: TrackListConfiguration,
    onTrackClick: (TrackEntity, Int) -> Unit,
    onTrackLongPress: (TrackEntity, Int) -> Unit = { _, _ -> },
    onTrackReorder: (Int, Int) -> Unit = { _, _ -> },
    onSwipeRemove: (TrackEntity, Int) -> Unit = { _, _ -> },
    onSwipeQueue: (TrackEntity, Int) -> Unit = { _, _ -> },
    onSwipeAddToFavorites: (TrackEntity, Int) -> Unit = { _, _ -> },
    onMultiSelectToggle: (TrackEntity, Int, Boolean) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedItems by remember { mutableStateOf(setOf<Int>()) }
    var isMultiSelectMode by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val dragDropState = rememberDragDropState(listState, onTrackReorder)

    var showFastScroll by remember { mutableStateOf(false) }
    val fastScrollAlpha by animateFloatAsState(
        targetValue = if (showFastScroll) 1f else 0f,
        animationSpec = tween(300),
        label = "fastScrollAlpha"
    )

    LaunchedEffect(listState) {
    snapshotFlow { listState.isScrollInProgress }
        .collectLatest { isScrolling ->
            if (isScrolling) {
                showFastScroll = true
            } else {
                delay(2000)
                showFastScroll = false
            }
        }
    }
    
    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    containerColor = skin.surfaceColor,
                    contentColor = skin.primaryTextColor,
                    actionColor = skin.accentColor,
                    snackbarData = data
                )
            }
        },
        modifier = modifier
    ) { paddingValues ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = config.contentPadding,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(
                    items = tracks,
                    key = { _, track -> track.id }
                ) { index, track ->
                    val isCurrentTrack = track.id == currentTrack?.id
                    val isSelected = selectedItems.contains(index)
                    val isDragged = dragDropState.draggingItemIndex == index

                    val itemModifier = if (config.isReorderable && !isMultiSelectMode) {
                        Modifier
                            .animateItemPlacement(animationSpec = tween(300))
                            .draggableItem(dragDropState, index)
                    } else {
                        Modifier.animateItemPlacement(animationSpec = tween(300))
                    }
                
                    TrackItem(
                        track = track,
                        index = index,
                        isCurrentTrack = isCurrentTrack,
                        isPlaying = isPlaying && isCurrentTrack,
                        isSelected = isSelected,
                        isMultiSelectMode = isMultiSelectMode,
                        isDragged = isDragged,
                        skin = skin,
                        showTrackNumber = config.showTrackNumbers,
                        showDuration = config.showDurations,
                        showAlbumArt = config.showAlbumArt,
                        enableSwipeActions = config.enableSwipeActions && !isMultiSelectMode,
                        isReorderable = config.isReorderable && !isMultiSelectMode,
                        onClick = { clickedTrack, clickedIndex ->
                            if (isMultiSelectMode) {
                                val newSelection = if (isSelected) {
                                    selectedItems - clickedIndex
                                } else {
                                    selectedItems + clickedIndex
                                }
                                selectedItems = newSelection
                                onMultiSelectToggle(clickedTrack, clickedIndex, !isSelected)
                            
                                if (newSelection.isEmpty()) {
                                    isMultiSelectMode = false
                                }
                            } else {
                                onTrackClick(clickedTrack, clickedIndex)
                            }
                        },
                        onLongPress = { longPressTrack, longPressIndex ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (config.enableMultiSelect && !config.isReorderable) {
                                isMultiSelectMode = true
                                selectedItems = setOf(longPressIndex)
                                onMultiSelectToggle(longPressTrack, longPressIndex, true)
                            } else if (!config.isReorderable) {
                                onTrackLongPress(longPressTrack, longPressIndex)
                            }
                        },
                        onSwipeRemove = { trackItem, trackIndex ->
                            onSwipeRemove(trackItem, trackIndex)
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "${trackItem.title} removed",
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    // ⚠️ Handle undo action
                                }
                            }
                        },
                        onSwipeQueue = { trackItem, trackIndex ->
                            onSwipeQueue(trackItem, trackIndex)
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = "Added ${trackItem.title} to queue",
                                    duration = SnackbarDuration.Short
                                )
                            }
                        },
                        snackbarHostState = snackbarHostState,
                        modifier = itemModifier
                    )
                }

                if (tracks.isEmpty()) {
                    item {
                        EmptyState(
                            skin = skin,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp)
                        )
                    }
                }
            }

        if (tracks.size > 50) {
            FastScrollIndicator(
                listState = listState,
                itemCount = tracks.size,
                skin = skin,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
                    .graphicsLayer { alpha = fastScrollAlpha }
            )
        }

        AnimatedVisibility(
            visible = isMultiSelectMode,
            enter = slideInVertically(
                initialOffsetY = { it }
            ),
            exit = slideOutVertically(
                targetOffsetY = { it }
            ),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            MultiSelectToolbar(
                selectedCount = selectedItems.size,
                skin = skin,
                onClearSelection = {
                    selectedItems = emptySet()
                    isMultiSelectMode = false
                },
                onSelectAll = {
                    selectedItems = tracks.indices.toSet()
                    tracks.forEachIndexed { index, track ->
                        onMultiSelectToggle(track, index, true)
                    }
                },
                onPlaySelected = {
                    val sortedSelection = selectedItems.sorted()
                    if (sortedSelection.isNotEmpty()) {
                        onTrackClick(tracks[sortedSelection.first()], sortedSelection.first())
                    }
                    selectedItems = emptySet()
                    isMultiSelectMode = false
                },
                onQueueSelected = {
                    selectedItems.sorted().forEach { index ->
                        onSwipeQueue(tracks[index], index)
                    }
                    selectedItems = emptySet()
                    isMultiSelectMode = false
                },
                onDeleteSelected = {
                    selectedItems.sortedDescending().forEach { index ->
                        onSwipeRemove(tracks[index], index)
                    }
                    selectedItems = emptySet()
                    isMultiSelectMode = false
                },
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackItem(
    track: TrackEntity,
    index: Int,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    isDragged: Boolean,
    skin: Skin,
    showTrackNumber: Boolean,
    showDuration: Boolean,
    showAlbumArt: Boolean,
    enableSwipeActions: Boolean,
    isReorderable: Boolean,
    onClick: (TrackEntity, Int) -> Unit,
    onLongPress: (TrackEntity, Int) -> Unit,
    onSwipeRemove: (TrackEntity, Int) -> Unit,
    onSwipeQueue: (TrackEntity, Int) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val elevation by animateFloatAsState(
        targetValue = if (isDragged) 8f else if (isCurrentTrack) 4f else 0f,
        animationSpec = tween(200),
        label = "elevation"
    )

    var swipeOffset by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = with(LocalDensity.current) { 80.dp.toPx() }

    val combinedClickableModifier = if (isDragged) {
        Modifier
    } else {
        Modifier.combinedClickable(
            onClick = { onClick(track, index) },
            onLongClick = { onLongPress(track, index) }
        )
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = swipeOffset
            }
            .then(
                if (enableSwipeActions) {
                    Modifier.draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            swipeOffset += delta
                        },
                        onDragStopped = { velocity ->
                            scope.launch {
                                val absOffset = kotlin.math.abs(swipeOffset)
                                if (absOffset > swipeThreshold) {
                                    if (swipeOffset > 0) {
                                        onSwipeQueue(track, index)
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    } else {
                                        onSwipeRemove(track, index)
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            animate(0f, swipeOffset) { value, _ -> swipeOffset = value }
                            }
                        }
                    )
                } else Modifier
            )
            .then(combinedClickableModifier),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> skin.selectedTrackBackground
                isCurrentTrack -> skin.currentTrackBackground
                else -> skin.trackItemBackground
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isMultiSelectMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(
                        checkedColor = skin.accentColor
                    )
                )
            } else if (showTrackNumber) {
                Box(
                    modifier = Modifier.size(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCurrentTrack && isPlaying) {
                        PlayingIndicator(
                            color = skin.accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text(
                            text = (index + 1).toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isCurrentTrack) skin.accentColor else skin.secondaryTextColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))

            if (showAlbumArt) {
                AlbumArt(
                    albumArtUri = track.albumArtUri,
                    size = 40.dp,
                    cornerRadius = 4.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isCurrentTrack) FontWeight.Medium else FontWeight.Normal
                    ),
                    color = if (isCurrentTrack) skin.accentColor else skin.primaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = skin.secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    if (track.album.isNotEmpty()) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = skin.secondaryTextColor
                        )
                        
                        Text(
                            text = track.album,
                            style = MaterialTheme.typography.bodySmall,
                            color = skin.secondaryTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showDuration && track.duration > 0) {
                    Text(
                        text = formatDuration(track.duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = skin.secondaryTextColor
                    )
                }
                
                if (isReorderable && !isMultiSelectMode) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Reorder",
                        tint = skin.secondaryTextColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        if (enableSwipeActions && kotlin.math.abs(swipeOffset) > 20) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = if (swipeOffset > 0) Arrangement.Start else Arrangement.End
            ) {
                val actionColor = if (kotlin.math.abs(swipeOffset) > swipeThreshold) {
                    skin.accentColor
                } else {
                    skin.secondaryTextColor
                }
                
                Icon(
                    imageVector = if (swipeOffset > 0) Icons.Default.PlaylistAdd else Icons.Default.Delete,
                    contentDescription = if (swipeOffset > 0) "Add to Queue" else "Remove",
                    tint = actionColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ⚠️ Dummy composable
@Composable
fun AlbumArt(albumArtUri: String?, size: androidx.compose.ui.unit.Dp, cornerRadius: androidx.compose.ui.unit.Dp) {
    Box(modifier = Modifier.size(size).background(Color.Gray, RoundedCornerShape(cornerRadius)))
}

@Composable
private fun PlayingIndicator(
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "playingIndicator")
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(3) { index ->
            val animationDelay = index * 100
            val height by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = animationDelay),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar$index"
            )
            
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(12.dp)
                    .background(
                        color = color,
                        shape = RoundedCornerShape(1.dp)
                    )
                    .graphicsLayer { scaleY = height }
            )
        }
    }
}

@Composable
private fun FastScrollIndicator(
    listState: LazyListState,
    itemCount: Int,
    skin: Skin,
    modifier: Modifier = Modifier
) {
    val firstVisibleIndex by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }
    
    val progress = if (itemCount > 0) {
        firstVisibleIndex.toFloat() / itemCount.toFloat()
    } else 0f
    
    Box(
        modifier = modifier
            .width(4.dp)
            .height(100.dp)
            .background(
                color = skin.secondaryTextColor.copy(alpha = 0.3f),
                shape = RoundedCornerShape(2.dp)
            )
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(20.dp)
                .background(
                    color = skin.accentColor,
                    shape = RoundedCornerShape(2.dp)
                )
                .offset(y = (80.dp * progress))
        )
    }
}

@Composable
private fun MultiSelectToolbar(
    selectedCount: Int,
    skin: Skin,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onPlaySelected: () -> Unit,
    onQueueSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = skin.surfaceColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$selectedCount selected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = skin.primaryTextColor
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = onSelectAll) {
                        Text("Select All", color = skin.accentColor)
                    }
                    
                    TextButton(onClick = onClearSelection) {
                        Text("Clear", color = skin.accentColor)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OutlinedButton(
                    onClick = onPlaySelected,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = skin.accentColor
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Play")
                }
                
                OutlinedButton(
                    onClick = onQueueSelected,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = skin.accentColor
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlaylistAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Queue")
                }
                
                OutlinedButton(
                    onClick = onDeleteSelected,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Remove")
                }
            }
        }
    }
}

@Composable
private fun EmptyPlaylistState(
    skin: Skin,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = skin.secondaryTextColor,
            modifier = Modifier.size(64.dp)
        )
        
        Text(
            text = "No tracks in this playlist",
            style = MaterialTheme.typography.headlineSmall,
            color = skin.primaryTextColor,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "Add some music to get started",
            style = MaterialTheme.typography.bodyMedium,
            color = skin.secondaryTextColor,
            textAlign = TextAlign.Center
        )
    }
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val hours = totalSeconds / 3600
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
