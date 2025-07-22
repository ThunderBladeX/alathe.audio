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
import com.alathea.mediascanner.entity.TrackEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistView(
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
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    
    var selectedItems by remember { mutableStateOf(setOf<Int>()) }
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var draggedItem by remember { mutableStateOf<Int?>(null) }
    
    val listState = rememberLazyListState()

    var showFastScroll by remember { mutableStateOf(false) }
    val fastScrollAlpha by animateFloatAsState(
        targetValue = if (showFastScroll) 1f else 0f,
        animationSpec = tween(300),
        label = "fastScrollAlpha"
    )

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            showFastScroll = true
        } else {
            delay(2000)
            showFastScroll = false
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = 16.dp,
                vertical = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(
                items = tracks,
                key = { _, track -> track.id }
            ) { index, track ->
                val isCurrentTrack = track.id == currentTrack?.id
                val isSelected = selectedItems.contains(index)
                val isDragged = draggedItem == index
                
                TrackItem(
                    track = track,
                    index = index,
                    isCurrentTrack = isCurrentTrack,
                    isPlaying = isPlaying && isCurrentTrack,
                    isSelected = isSelected,
                    isMultiSelectMode = isMultiSelectMode,
                    isDragged = isDragged,
                    skin = skin,
                    showTrackNumber = showTrackNumbers,
                    showDuration = showDurations,
                    showAlbumArt = showAlbumArt,
                    enableSwipeActions = enableSwipeActions && !isMultiSelectMode,
                    isReorderable = isReorderable && !isMultiSelectMode,
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
                        if (enableMultiSelect) {
                            isMultiSelectMode = true
                            selectedItems = setOf(longPressIndex)
                            onMultiSelectToggle(longPressTrack, longPressIndex, true)
                        } else {
                            onTrackLongPress(longPressTrack, longPressIndex)
                        }
                    },
                    onSwipeRemove = if (enableSwipeActions) { swipeTrack, swipeIndex ->
                        onSwipeRemove(swipeTrack, swipeIndex)
                    } else null,
                    onSwipeQueue = if (enableSwipeActions) { swipeTrack, swipeIndex ->
                        onSwipeQueue(swipeTrack, swipeIndex)
                    } else null,
                    onSwipeAddToFavorites = if (enableSwipeActions) { swipeTrack, swipeIndex ->
                        onSwipeAddToFavorites(swipeTrack, swipeIndex)
                    } else null,
                    onDragStart = if (isReorderable) {
                        { draggedItem = index }
                    } else null,
                    onDragEnd = if (isReorderable) {
                        { fromIndex, toIndex ->
                            draggedItem = null
                            if (fromIndex != toIndex) {
                                onTrackReorder(fromIndex, toIndex)
                            }
                        }
                    } else null,
                    modifier = Modifier.animateItemPlacement(
                        animationSpec = tween(300)
                    )
                )
            }

            if (tracks.isEmpty()) {
                item {
                    EmptyPlaylistState(
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
                initialOffsetY = { it },
                animationSpec = tween(300)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(300)
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
                    // Play first selected track and queue the rest
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
    onSwipeRemove: ((TrackEntity, Int) -> Unit)?,
    onSwipeQueue: ((TrackEntity, Int) -> Unit)?,
    onSwipeAddToFavorites: ((TrackEntity, Int) -> Unit)?,
    onDragStart: (() -> Unit)?,
    onDragEnd: ((Int, Int) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    val elevation by animateFloatAsState(
        targetValue = if (isDragged) 8f else if (isCurrentTrack) 4f else 0f,
        animationSpec = tween(200),
        label = "elevation"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isDragged) 1.02f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    var swipeOffset by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = with(LocalDensity.current) { 80.dp.toPx() }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = elevation
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
                            val absOffset = kotlin.math.abs(swipeOffset)
                            when {
                                absOffset > swipeThreshold && swipeOffset > 0 -> {
                                    // Swipe right - Queue
                                    onSwipeQueue?.invoke(track, index)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                absOffset > swipeThreshold && swipeOffset < 0 -> {
                                    // Swipe left - Remove
                                    onSwipeRemove?.invoke(track, index)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                            swipeOffset = 0f
                        }
                    )
                } else Modifier
            )
            .combinedClickable(
                onClick = { onClick(track, index) },
                onLongClick = { onLongPress(track, index) }
            ),
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
                    onCheckedChange = null, // Handled by card click
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
                        // Animated playing indicator
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
                
                if (isReorderable && onDragStart != null) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Reorder",
                        tint = skin.secondaryTextColor,
                        modifier = Modifier
                            .size(20.dp)
                            .combinedClickable(
                                onClick = { /* No single click action */ },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDragStart()
                                }
                            )
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
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
