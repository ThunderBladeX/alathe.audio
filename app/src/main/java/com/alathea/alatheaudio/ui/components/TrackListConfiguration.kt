package com.alathea.alatheaudio.ui.components

import androidx.compose.runtime.Immutable

/**
 * Defines the behavior and appearance of a TrackListView.
 *
 * This object is passed to the TrackListView composable to configure its features,
 * such as reordering, multi-select, swipe actions, and visual elements.
 * Using a configuration object like this makes the TrackListView more reusable and
 * its call sites cleaner.
 */
@Immutable
data class TrackListConfiguration(
    val isReorderable: Boolean = false,
    val enableMultiSelect: Boolean = false,
    val enableSwipeActions: Boolean = false,
    val showTrackNumbers: Boolean = true,
    val showDurations: Boolean = true,
    val showAlbumArt: Boolean = true
) {
    companion object {
        fun reorderableQueue() = TrackListConfiguration(
            isReorderable = true,
            enableSwipeActions = true,
            enableMultiSelect = false,
            showTrackNumbers = true,
            showAlbumArt = true,
            showDurations = true,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        )

        fun libraryView() = TrackListConfiguration(
            isReorderable = false,
            enableSwipeActions = true,
            enableMultiSelect = true,
            showTrackNumbers = false,
            showAlbumArt = true,
            showDurations = true,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        )

        fun staticAlbumView() = TrackListConfiguration(
            isReorderable = false,
            enableSwipeActions = false,
            enableMultiSelect = false,
            showTrackNumbers = true,
            showAlbumArt = false,
            showDurations = true,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
