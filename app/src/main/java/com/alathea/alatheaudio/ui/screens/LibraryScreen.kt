package com.alathea.alatheaudio.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.alathea.alatheaudio.R
import com.alathea.alatheaudio.data.model.*
import com.alathea.alatheaudio.ui.components.*
import com.alathea.alatheaudio.ui.theme.LocalSkin
import com.alathea.alatheaudio.utils.SortOrder
import com.alathea.alatheaudio.utils.FilterType
import com.alathea.alatheaudio.utils.formatDuration
import com.alathea.alatheaudio.utils.formatFileSize
import com.alathea.alatheaudio.viewmodel.LibraryTab
import com.alathea.alatheaudio.viewmodel.LibraryViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onTrackClick: (Track) -> Unit,
    onNavigateToNowPlaying: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val skin = LocalSkin.current
    val scope = rememberCoroutineScope()

    val selectedTab by viewModel.selectedLibraryTab.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val filterType by viewModel.filterType.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isScanningLibrary by viewModel.isScanningLibrary.collectAsStateWithLifecycle()
    val scanProgress by viewModel.scanProgress.collectAsStateWithLifecycle()
    val scanStatusMessage by viewModel.scanStatusMessage.collectAsStateWithLifecycle()
    val libraryStats by viewModel.libraryStats.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedTracks by viewModel.selectedTracks.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()

    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()

    val recentlyAdded by viewModel.recentlyAdded.collectAsStateWithLifecycle()
    val mostPlayed by viewModel.mostPlayed.collectAsStateWithLifecycle()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()

    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showLibraryStats by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listStates = remember {
        mapOf(
            LibraryTab.TRACKS to LazyListState(),
            LibraryTab.ALBUMS to LazyGridState(),
            LibraryTab.ARTISTS to LazyGridState(),
            LibraryTab.GENRES to LazyListState(),
            LibraryTab.PLAYLISTS to LazyListState(),
            LibraryTab.FOLDERS to LazyListState()
        )
    }

    LaunchedEffect(selectedTab, tracks, albums, artists) {
        delay(500)
        when (selectedTab) {
            LibraryTab.TRACKS -> viewModel.preloadAlbumArt(tracks.take(20))
            LibraryTab.ALBUMS -> viewModel.preloadAlbumArt(albums.take(20).map { 
                Track(id = 0, albumArtUri = it.albumArtUri, title = "", artist = "", album = "", duration = 0) 
            })
            LibraryTab.ARTISTS -> viewModel.preloadAlbumArt(artists.take(20).map { 
                Track(id = 0, albumArtUri = it.imageUri, title = "", artist = "", album = "", duration = 0) 
            })
            else -> {}
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) {
        LargeTopAppBar(
            title = {
                AnimatedContent(
                    targetState = searchQuery.isNotEmpty(),
                    transitionSpec = {
                        slideInVertically() + fadeIn() with slideOutVertically() + fadeOut()
                    }
                ) { isSearching ->
                    if (isSearching) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = viewModel::updateSearchQuery,
                            placeholder = { 
                                Text(
                                    text = stringResource(R.string.search_library),
                                    color = skin.secondaryTextColor
                                ) 
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = skin.controlColor
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = viewModel::clearSearch) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear search",
                                            tint = skin.controlColor
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Search
                            ),
                            keyboardActions = KeyboardActions(
                                onSearch = { /* Search is automatic via StateFlow */ }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = skin.primaryTextColor,
                                unfocusedTextColor = skin.primaryTextColor,
                                focusedBorderColor = skin.accentColor,
                                unfocusedBorderColor = skin.controlColor.copy(alpha = 0.5f)
                            )
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.library),
                                style = MaterialTheme.typography.headlineMedium,
                                color = skin.primaryTextColor,
                                fontWeight = FontWeight.Bold
                            )

                            if (libraryStats.totalTracks > 0) {
                                TextButton(
                                    onClick = { showLibraryStats = true }
                                ) {
                                    Text(
                                        text = "${libraryStats.totalTracks} tracks",
                                        color = skin.secondaryTextColor,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        if (searchQuery.isNotEmpty()) {
                            viewModel.clearSearch()
                        } else {
                            scope.launch {
                                delay(100)
                                focusRequester.requestFocus()
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (searchQuery.isNotEmpty()) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = if (searchQuery.isNotEmpty()) "Close search" else "Search",
                        tint = skin.controlColor
                    )
                }

                IconButton(onClick = { showSortMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = "Sort",
                        tint = skin.controlColor
                    )
                }

                if (selectedTab == LibraryTab.TRACKS) {
                    IconButton(onClick = { showFilterMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter",
                            tint = if (filterType != FilterType.ALL) skin.accentColor else skin.controlColor
                        )
                    }
                }

                if (selectedTab == LibraryTab.TRACKS) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.toggleSelectionMode()
                        }
                    ) {
                        Icon(
                            imageVector = if (isSelectionMode) Icons.Default.CheckCircle else Icons.Default.CheckCircleOutline,
                            contentDescription = "Selection mode",
                            tint = if (isSelectionMode) skin.accentColor else skin.controlColor
                        )
                    }
                }

                MoreOptionsMenu(
                    onStartScan = viewModel::startMediaScan,
                    onStartIncrementalScan = viewModel::startIncrementalScan,
                    onCancelScan = viewModel::cancelScan,
                    onShuffleAll = viewModel::shuffleAll,
                    isScanningLibrary = isScanningLibrary
                )
            },
            colors = TopAppBarDefaults.largeTopAppBarColors(
                containerColor = skin.surfaceColor,
                titleContentColor = skin.primaryTextColor,
                actionIconContentColor = skin.controlColor
            ),
            scrollBehavior = scrollBehavior
        )

        AnimatedVisibility(
            visible = isScanningLibrary,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut()
        ) {
            LibraryScanProgress(
                progress = scanProgress,
                statusMessage = scanStatusMessage,
                onCancel = viewModel::cancelScan
            )
        }

        AnimatedVisibility(
            visible = isSelectionMode && selectedTracks.isNotEmpty(),
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut()
        ) {
            SelectionModeActions(
                selectedCount = selectedTracks.size,
                onSelectAll = viewModel::selectAllTracks,
                onClearSelection = viewModel::clearSelection,
                onAddToPlaylist = { playlistId -> viewModel.addSelectedToPlaylist(playlistId) },
                onCreatePlaylist = { name -> viewModel.createPlaylistFromSelected(name) },
                onDelete = viewModel::deleteSelectedTracks
            )
        }

        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = skin.surfaceColor,
            contentColor = skin.primaryTextColor,
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                    color = skin.accentColor,
                    height = 3.dp
                )
            }
        ) {
            LibraryTab.values().forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { viewModel.selectLibraryTab(tab) },
                    text = {
                        Text(
                            text = stringResource(getTabNameResource(tab)),
                            color = if (selectedTab == tab) skin.accentColor else skin.secondaryTextColor,
                            fontWeight = if (selectedTab == tab) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (searchQuery.isNotEmpty() && searchQuery.length >= 2) {
                SearchResultsContent(
                    searchResults = searchResults,
                    searchQuery = searchQuery,
                    onTrackClick = onTrackClick,
                    onAlbumClick = { album ->
                        scope.launch {
                            val albumTracks = viewModel.getAlbumTracks(album.id)
                            if (albumTracks.isNotEmpty()) {
                                onTrackClick(albumTracks.first())
                            }
                        }
                    },
                    onArtistClick = { artist ->
                        scope.launch {
                            val artistTracks = viewModel.getArtistTracks(artist.id)
                            if (artistTracks.isNotEmpty()) {
                                onTrackClick(artistTracks.first())
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                when (selectedTab) {
                    LibraryTab.TRACKS -> {
                        TracksContent(
                            tracks = tracks,
                            isSelectionMode = isSelectionMode,
                            selectedTracks = selectedTracks,
                            onTrackClick = onTrackClick,
                            onTrackLongClick = { trackId ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (!isSelectionMode) {
                                    viewModel.toggleSelectionMode()
                                }
                                viewModel.toggleTrackSelection(trackId)
                            },
                            onToggleSelection = viewModel::toggleTrackSelection,
                            recentlyAdded = recentlyAdded,
                            mostPlayed = mostPlayed,
                            recentlyPlayed = recentlyPlayed,
                            listState = listStates[LibraryTab.TRACKS] as LazyListState
                        )
                    }
                    LibraryTab.ALBUMS -> {
                        AlbumsContent(
                            albums = albums,
                            onAlbumClick = { album ->
                                scope.launch {
                                    val albumTracks = viewModel.getAlbumTracks(album.id)
                                    if (albumTracks.isNotEmpty()) {
                                        onTrackClick(albumTracks.first())
                                    }
                                }
                            },
                            gridState = listStates[LibraryTab.ALBUMS] as LazyGridState
                        )
                    }
                    LibraryTab.ARTISTS -> {
                        ArtistsContent(
                            artists = artists,
                            onArtistClick = { artist ->
                                scope.launch {
                                    val artistTracks = viewModel.getArtistTracks(artist.id)
                                    if (artistTracks.isNotEmpty()) {
                                        onTrackClick(artistTracks.first())
                                    }
                                }
                            },
                            gridState = listStates[LibraryTab.ARTISTS] as LazyGridState
                        )
                    }
                    LibraryTab.GENRES -> {
                        GenresContent(
                            genres = genres,
                            onGenreClick = { genre ->
                                scope.launch {
                                    val genreTracks = viewModel.getGenreTracks(genre.name)
                                    if (genreTracks.isNotEmpty()) {
                                        onTrackClick(genreTracks.first())
                                    }
                                }
                            },
                            listState = listStates[LibraryTab.GENRES] as LazyListState
                        )
                    }
                    LibraryTab.PLAYLISTS -> {
                        PlaylistsContent(
                            playlists = playlists,
                            onPlaylistClick = { playlist ->
                            },
                            listState = listStates[LibraryTab.PLAYLISTS] as LazyListState
                        )
                    }
                    LibraryTab.FOLDERS -> {
                        FoldersContent(
                            folders = folders,
                            onFolderClick = { folder ->
                                scope.launch {
                                    val folderTracks = viewModel.getFolderTracks(folder.path)
                                    if (folderTracks.isNotEmpty()) {
                                        onTrackClick(folderTracks.first())
                                    }
                                }
                            },
                            listState = listStates[LibraryTab.FOLDERS] as LazyListState
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isLoading,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                CircularProgressIndicator(
                    color = skin.accentColor,
                    strokeWidth = 3.dp
                )
            }
        }
    }

    SortMenuDropdown(
        expanded = showSortMenu,
        onDismiss = { showSortMenu = false },
        currentSortOrder = sortOrder,
        onSortOrderChange = { order ->
            viewModel.setSortOrder(order)
            showSortMenu = false
        }
    )

    if (selectedTab == LibraryTab.TRACKS) {
        FilterMenuDropdown(
            expanded = showFilterMenu,
            onDismiss = { showFilterMenu = false },
            currentFilterType = filterType,
            onFilterTypeChange = { filter ->
                viewModel.setFilterType(filter)
                showFilterMenu = false
            }
        )
    }

    if (showLibraryStats) {
        LibraryStatsDialog(
            stats = libraryStats,
            onDismiss = { showLibraryStats = false }
        )
    }
}

@Composable
fun TracksContent(
    tracks: List<Track>,
    isSelectionMode: Boolean,
    selectedTracks: Set<Long>,
    onTrackClick: (Track) -> Unit,
    onTrackLongClick: (Long) -> Unit,
    onToggleSelection: (Long) -> Unit,
    recentlyAdded: List<Track>,
    mostPlayed: List<Track>,
    recentlyPlayed: List<Track>,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (recentlyAdded.isNotEmpty()) {
            item {
                QuickAccessSection(
                    title = stringResource(R.string.recently_added),
                    tracks = recentlyAdded.take(10),
                    onTrackClick = onTrackClick,
                    onSeeAll = { /* Navigate to full recently added */ }
                )
            }
        }
        
        if (mostPlayed.isNotEmpty()) {
            item {
                QuickAccessSection(
                    title = stringResource(R.string.most_played),
                    tracks = mostPlayed.take(10),
                    onTrackClick = onTrackClick,
                    onSeeAll = { /* Navigate to full most played */ }
                )
            }
        }
        
        if (recentlyPlayed.isNotEmpty()) {
            item {
                QuickAccessSection(
                    title = stringResource(R.string.recently_played),
                    tracks = recentlyPlayed.take(10),
                    onTrackClick = onTrackClick,
                    onSeeAll = { /* Navigate to full recently played */ }
                )
            }
        }

        if (tracks.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.all_tracks),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                )
            }
        }

        items(
            items = tracks,
            key = { track -> track.id }
        ) { track ->
            TrackItem(
                track = track,
                isSelected = selectedTracks.contains(track.id),
                isSelectionMode = isSelectionMode,
                onClick = {
                    if (isSelectionMode) {
                        onToggleSelection(track.id)
                    } else {
                        onTrackClick(track)
                    }
                },
                onLongClick = { onTrackLongClick(track.id) },
                modifier = Modifier.animateItemPlacement()
            )
        }

        if (tracks.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.MusicNote,
                    title = stringResource(R.string.no_tracks_found),
                    subtitle = stringResource(R.string.try_different_search_or_scan_library),
                    modifier = Modifier.padding(32.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumsContent(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit,
    gridState: LazyGridState,
    modifier: Modifier = Modifier
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(minSize = 160.dp),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 16.dp
    ) {
        items(
            items = albums,
            key = { album -> album.id }
        ) { album ->
            AlbumGridItem(
                album = album,
                onClick = { onAlbumClick(album) },
                modifier = Modifier.animateItemPlacement()
            )
        }
        
        if (albums.isEmpty()) {
            item(span = StaggeredGridItemSpan.FullLine) {
                EmptyState(
                    icon = Icons.Outlined.Album,
                    title = stringResource(R.string.no_albums_found),
                    subtitle = stringResource(R.string.try_different_search_or_scan_library),
                    modifier = Modifier.padding(32.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistsContent(
    artists: List<Artist>,
    onArtistClick: (Artist) -> Unit,
    gridState: LazyGridState,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(
            items = artists,
            key = { artist -> artist.id }
        ) { artist ->
            ArtistGridItem(
                artist = artist,
                onClick = { onArtistClick(artist) },
                modifier = Modifier.animateItemPlacement()
            )
        }
        
        if (artists.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    icon = Icons.Outlined.Person,
                    title = stringResource(R.string.no_artists_found),
                    subtitle = stringResource(R.string.try_different_search_or_scan_library),
                    modifier = Modifier.padding(32.dp)
                )
            }
        }
    }
}

@Composable
fun GenresContent(
    genres: List<Genre>,
    onGenreClick: (Genre) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(
            items = genres,
            key = { genre -> genre.name }
        ) { genre ->
            GenreItem(
                genre = genre,
                onClick = { onGenreClick(genre) },
                modifier = Modifier.animateItemPlacement()
            )
        }
        
        if (genres.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.Category,
                    title = stringResource(R.string.no_genres_found),
                    subtitle = stringResource(R.string.try_different_search_or_scan_library),
                    modifier = Modifier.padding(32.dp)
                )
            }
        }
    }
}

@Composable
fun PlaylistsContent(
    playlists: List<Playlist>,
    onPlaylistClick: (Playlist) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(
            items = playlists,
            key = { playlist -> playlist.id }
        ) { playlist ->
            PlaylistItem(
                playlist = playlist,
                onClick = { onPlaylistClick(playlist) },
                modifier = Modifier.animateItemPlacement()
            )
        }
        
        if (playlists.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.PlaylistPlay,
                    title = stringResource(R.string.no_playlists_found),
                    subtitle = stringResource(R.string.create_playlist_to_get_started),
                    modifier = Modifier.padding(32.dp)
                )
            }
        }
    }
}

@Composable
fun FoldersContent(
    folders: List<Folder>,
    onFolderClick: (Folder) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(
            items = folders,
            key = { folder -> folder.path }
        ) { folder ->
            FolderItem(
                folder = folder,
                onClick = { onFolderClick(folder) },
                modifier = Modifier.animateItemPlacement()
            )
        }
        
        if (folders.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.Folder,
                    title = stringResource(R.string.no_folders_found),
                    subtitle = stringResource(R.string.try_different_search_or_scan_library),
                    modifier = Modifier.padding(32.dp)
                )
            }
        }
    }
}

@Composable
fun SearchResultsContent(
    searchResults: SearchResult,
    searchQuery: String,
    onTrackClick: (Track) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    modifier: Modifier = Modifier
) {
    val skin = LocalSkin.current
    
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (searchResults.isEmpty) {
            item {
                EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = stringResource(R.string.no_results_found),
                    subtitle = stringResource(R.string.try_different_search_terms, searchQuery),
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            if (searchResults.tracks.isNotEmpty()) {
                item {
                    SearchSectionHeader(
                        title = stringResource(R.string.tracks),
                        count = searchResults.tracks.size
                    )
                }
                
                items(
                    items = searchResults.tracks.take(10),
                    key = { track -> "track_${track.id}" }
                ) { track ->
                    TrackItem(
                        track = track,
                        isSelected = false,
                        isSelectionMode = false,
                        onClick = { onTrackClick(track) },
                        onLongClick = { },
                        showIndex = false
                    )
                }
                
                if (searchResults.tracks.size > 10) {
                    item {
                        ShowMoreButton(
                            count = searchResults.tracks.size - 10,
                            onClick = { /* Show all tracks */ }
                        )
                    }
                }
            }

            if (searchResults.albums.isNotEmpty()) {
                item {
                    SearchSectionHeader(
                        title = stringResource(R.string.albums),
                        count = searchResults.albums.size
                    )
                }
                
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = searchResults.albums.take(10),
                            key = { album -> "album_${album.id}" }
                        ) { album ->
                            AlbumGridItem(
                                album = album,
                                onClick = { onAlbumClick(album) },
                                modifier = Modifier.width(140.dp)
                            )
                        }
                    }
                }
            }

            if (searchResults.artists.isNotEmpty()) {
                item {
                    SearchSectionHeader(
                        title = stringResource(R.string.artists),
                        count = searchResults.artists.size
                    )
                }
                
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = searchResults.artists.take(10),
                            key = { artist -> "artist_${artist.id}" }
                        ) { artist ->
                            ArtistGridItem(
                                artist = artist,
                                onClick = { onArtistClick(artist) },
                                modifier = Modifier.width(120.dp)
                            )
                        }
                    }
                }
            }

            if (searchResults.genres.isNotEmpty()) {
                item {
                    SearchSectionHeader(
                        title = stringResource(R.string.genres),
                        count = searchResults.genres.size
                    )
                }
                
                items(
                    items = searchResults.genres.take(10),
                    key = { genre -> "genre_${genre.name}" }
                ) { genre ->
                    GenreItem(
                        genre = genre,
                        onClick = { /* Handle genre click */ }
                    )
                }
            }

            if (searchResults.playlists.isNotEmpty()) {
                item {
                    SearchSectionHeader(
                        title = stringResource(R.string.playlists),
                        count = searchResults.playlists.size
                    )
                }
                
                items(
                    items = searchResults.playlists.take(10),
                    key = { playlist -> "playlist_${playlist.id}" }
                ) { playlist ->
                    PlaylistItem(
                        playlist = playlist,
                        onClick = { /* Handle playlist click */ }
                    )
                }
            }
        }
    }
}

@Composable
fun SearchSectionHeader(
    title: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    val skin = LocalSkin.current
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = skin.primaryTextColor
        )
        
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = skin.secondaryTextColor
        )
    }
}

@Composable
fun ShowMoreButton(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val skin = LocalSkin.current
    
    TextButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.show_more_count, count),
            color = skin.accentColor,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun QuickAccessSection(
    title: String,
    tracks: List<Track>,
    onTrackClick: (Track) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val skin = LocalSkin.current
    
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = skin.primaryTextColor
            )
            
            TextButton(onClick = onSeeAll) {
                Text(
                    text = stringResource(R.string.see_all),
                    color = skin.accentColor,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = tracks,
                key = { track -> "quick_${track.id}" }
            ) { track ->
                QuickAccessTrackItem(
                    track = track,
                    onClick = { onTrackClick(track) },
                    modifier = Modifier.width(160.dp)
                )
            }
        }
    }
}

@Composable
fun QuickAccessTrackItem(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val skin = LocalSkin.current
    
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = skin.surfaceVariantColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AlbumArt(
                albumArtUri = track.albumArtUri,
                size = 136.dp,
                cornerRadius = 8.dp
            )
            
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = skin.primaryTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = skin.secondaryTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun LibraryScanProgress(
    progress: Float,
    statusMessage: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val skin = LocalSkin.current
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = skin.surfaceVariantColor
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.scanning_library),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = skin.primaryTextColor
                )
                
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel scan",
                        tint = skin.controlColor
                    )
                }
            }
            
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth(),
                color = skin.accentColor,
                trackColor = skin.progressTrackColor
            )
            
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = skin.secondaryTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SelectionModeActions(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onAddToPlaylist: (Long) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val skin = LocalSkin.current
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = skin.accentColor.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.selected_count, selectedCount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = skin.primaryTextColor
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onSelectAll) {
                    Icon(
                        imageVector = Icons.Default.SelectAll,
                        contentDescription = "Select all",
                        tint = skin.controlColor
                    )
                }
                
                IconButton(onClick = { /* Show playlist menu */ }) {
                    Icon(
                        imageVector = Icons.Default.PlaylistAdd,
                        contentDescription = "Add to playlist",
                        tint = skin.controlColor
                    )
                }
                
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = skin.controlColor
                    )
                }
                
                IconButton(onClick = onClearSelection) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear selection",
                        tint = skin.controlColor
                    )
                }
            }
        }
    }
}

@Composable
fun MoreOptionsMenu(
    onStartScan: () -> Unit,
    onStartIncrementalScan: () -> Unit,
    onCancelScan: () -> Unit,
    onShuffleAll: () -> Unit,
    isScanningLibrary: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More options"
            )
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.shuffle_all)) },
                onClick = {
                    onShuffleAll()
                    expanded = false
                },
                leadingIcon = {
                    Icon(Icons.Default.Shuffle, contentDescription = null)
                }
            )
            
            Divider()
            
            if (isScanningLibrary) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.cancel_scan)) },
                    onClick = {
                        onCancelScan()
                        expanded = false
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Stop, contentDescription = null)
                    }
                )
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.scan_library)) },
                    onClick = {
                        onStartScan()
                        expanded = false
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                )
                
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.incremental_scan)) },
                    onClick = {
                        onStartIncrementalScan()
                        expanded = false
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Update, contentDescription = null)
                    }
                )
            }
        }
    }
}

@Composable
private fun getTabNameResource(tab: LibraryTab): Int {
    return when (tab) {
        LibraryTab.TRACKS -> R.string.tracks
        LibraryTab.ALBUMS -> R.string.albums
        LibraryTab.ARTISTS -> R.string.artists
        LibraryTab.GENRES -> R.string.genres
        LibraryTab.PLAYLISTS -> R.string.playlists
        LibraryTab.FOLDERS -> R.string.folders
    }
}
