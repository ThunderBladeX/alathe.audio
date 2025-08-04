package com.alathea.alatheaudio.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.staggeredgrid.*
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.alathea.alatheaudio.R
import com.alathea.alatheaudio.data.model.*
import com.alathea.alatheaudio.ui.components.*
import com.alathea.alatheaudio.ui.theme.LocalSkin
import com.alathea.alatheaudio.utils.SortOrder
import com.alathea.alatheaudio.utils.FilterType
import com.alathea.alatheaudio.viewmodel.LibraryTab
import com.alathea.alatheaudio.viewmodel.LibraryViewModel
import com.alathea.alatheaudio.viewmodel.NowPlayingViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    navController: NavHostController,
    onTrackClick: (Track) -> Unit,
    onPlayQueue: (tracks: List<Track>, startTrack: Track) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val skin = LocalSkin.current
    val scope = rememberCoroutineScope()

    val selectedTab by viewModel.selectedLibraryTab.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val isScanningLibrary by viewModel.isScanningLibrary.collectAsStateWithLifecycle()

    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showLibraryStats by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LibraryTopAppBar(
                viewModel = viewModel,
                scrollBehavior = scrollBehavior,
                selectedTab = selectedTab,
                onShowSortMenu = { showSortMenu = true },
                onShowFilterMenu = { showFilterMenu = true },
                onShowLibraryStats = { showLibraryStats = true },
                onToggleSelectionMode = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.toggleSelectionMode()
                }
            )
        },
        containerColor = skin.surfaceColor
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            AnimatedVisibility(visible = isScanningLibrary) {
                val scanProgress by viewModel.scanProgress.collectAsStateWithLifecycle()
                val scanStatusMessage by viewModel.scanStatusMessage.collectAsStateWithLifecycle()
                LibraryScanProgress(
                    progress = scanProgress,
                    statusMessage = scanStatusMessage,
                    onCancel = viewModel::cancelScan
                )
            }

            AnimatedVisibility(visible = isSelectionMode) {
                SelectionModeActions(
                    viewModel = viewModel,
                    onShowAddToPlaylistDialog = { showAddToPlaylistDialog = true }
                )
            }

            LibraryTabs(
                selectedTab = selectedTab,
                onTabSelected = { viewModel.selectLibraryTab(it) }
            )

            LibraryContent(
                viewModel = viewModel,
                navController = navController,
                onTrackClick = onTrackClick,
                onPlayQueue = onPlayQueue,
                onLongTrackClick = { trackId ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (!isSelectionMode) {
                        viewModel.toggleSelectionMode()
                    }
                    viewModel.toggleTrackSelection(trackId)
                }
            )
        }
    }

    SortMenuDropdown(
        expanded = showSortMenu,
        onDismiss = { showSortMenu = false },
        currentSortOrder = viewModel.sortOrder.collectAsStateWithLifecycle().value,
        onSortOrderChange = { order ->
            viewModel.setSortOrder(order)
            showSortMenu = false
        }
    )

    if (selectedTab == LibraryTab.TRACKS) {
        FilterMenuDropdown(
            expanded = showFilterMenu,
            onDismiss = { showFilterMenu = false },
            currentFilterType = viewModel.filterType.collectAsStateWithLifecycle().value,
            onFilterTypeChange = { filter ->
                viewModel.setFilterType(filter)
                showFilterMenu = false
            }
        )
    }

    if (showLibraryStats) {
        LibraryStatsDialog(
            stats = viewModel.libraryStats.collectAsStateWithLifecycle().value,
            onDismiss = { showLibraryStats = false }
        )
    }

    if (showAddToPlaylistDialog) {
        val playlists by viewModel.playlists.collectAsStateWithLifecycle()
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { showAddToPlaylistDialog = false },
            onAddToExistingPlaylist = { playlistId ->
                viewModel.addSelectedToPlaylist(playlistId)
                showAddToPlaylistDialog = false
            },
            onCreateNewPlaylist = { name ->
                viewModel.createPlaylistFromSelected(name)
                showAddToPlaylistDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTopAppBar(
    viewModel: LibraryViewModel,
    scrollBehavior: TopAppBarScrollBehavior,
    selectedTab: LibraryTab,
    onShowSortMenu: () -> Unit,
    onShowFilterMenu: () -> Unit,
    onShowLibraryStats: () -> Unit,
    onToggleSelectionMode: () -> Unit
) {
    val skin = LocalSkin.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filterType by viewModel.filterType.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val isScanningLibrary by viewModel.isScanningLibrary.collectAsStateWithLifecycle()
    val libraryStats by viewModel.libraryStats.collectAsStateWithLifecycle()

    LargeTopAppBar(
        title = {
            if (searchQuery.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::updateSearchQuery,
                    placeholder = { Text(text = stringResource(R.string.search_library)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = viewModel::clearSearch) {
                                Icon(Icons.Default.Clear, "Clear search")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    colors = OutlinedTextFieldDefaults.colors(
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
                        fontWeight = FontWeight.Bold
                    )
                    if (libraryStats.totalTracks > 0) {
                        TextButton(onClick = onShowLibraryStats) {
                            Text(
                                "${libraryStats.totalTracks} tracks",
                                color = skin.secondaryTextColor,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        },
        actions = {
            IconButton(onClick = { if (searchQuery.isEmpty()) scope.launch { focusRequester.requestFocus() } else viewModel.clearSearch() }) {
                Icon(Icons.Default.Search, "Search")
            }
            IconButton(onClick = onShowSortMenu) { Icon(Icons.Default.Sort, "Sort") }
            if (selectedTab == LibraryTab.TRACKS) {
                IconButton(onClick = onShowFilterMenu) {
                    Icon(
                        Icons.Default.FilterList, "Filter",
                        tint = if (filterType != FilterType.ALL) skin.accentColor else LocalContentColor.current
                    )
                }
            }
            if (selectedTab == LibraryTab.TRACKS) {
                IconButton(onClick = onToggleSelectionMode) {
                    Icon(
                        if (isSelectionMode) Icons.Default.CheckCircle else Icons.Default.CheckCircleOutline,
                        "Selection mode",
                        tint = if (isSelectionMode) skin.accentColor else LocalContentColor.current
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
            scrolledContainerColor = skin.surfaceColor
        ),
        scrollBehavior = scrollBehavior
    )
}

@Composable
private fun LibraryTabs(
    selectedTab: LibraryTab,
    onTabSelected: (LibraryTab) -> Unit
) {
    val skin = LocalSkin.current
    TabRow(
        selectedTabIndex = selectedTab.ordinal,
        containerColor = skin.surfaceColor,
        indicator = { tabPositions ->
            TabRowDefaults.Indicator(
                Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                height = 3.dp,
                color = skin.accentColor
            )
        }
    ) {
        LibraryTab.values().forEach { tab ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                text = {
                    Text(
                        text = stringResource(getTabNameResource(tab)),
                        color = if (selectedTab == tab) skin.accentColor else skin.secondaryTextColor,
                    )
                }
            )
        }
    }
}

@Composable
private fun LibraryContent(
    viewModel: LibraryViewModel,
    navController: NavHostController,
    onTrackClick: (Track) -> Unit,
    onPlayQueue: (tracks: List<Track>, startTrack: Track) -> Unit,
    onLongTrackClick: (Long) -> Unit
) {
    val scope = rememberCoroutineScope()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedLibraryTab.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        if (searchQuery.isNotEmpty()) {
            val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
            SearchResultsContent(
                searchResults = searchResults,
                searchQuery = searchQuery,
                onTrackClick = onTrackClick,
                onAlbumClick = { navController.navigate("album_detail/${it.id}") },
                onArtistClick = { navController.navigate("artist_detail/${it.id}") },
                onGenreClick = { genre ->
                    scope.launch {
                        val tracks = viewModel.getGenreTracks(genre.name)
                        if (tracks.isNotEmpty()) onPlayQueue(tracks, tracks.first())
                    }
                },
                onPlaylistClick = { playlist ->
                    navController.navigate("playlist/${playlist.id}")
                },
                onShowMore = { tab -> viewModel.selectLibraryTab(tab) }
            )
        } else {
            when (selectedTab) {
                LibraryTab.TRACKS -> TracksContent(
                    viewModel = viewModel,
                    onTrackClick = onTrackClick,
                    onLongTrackClick = onLongTrackClick
                )
                LibraryTab.ALBUMS -> {
                    val albums by viewModel.albums.collectAsStateWithLifecycle()
                    AlbumsContent(
                        albums = albums,
                        onAlbumClick = { navController.navigate("album_detail/${it.id}") }
                    )
                }
                LibraryTab.ARTISTS -> {
                    val artists by viewModel.artists.collectAsStateWithLifecycle()
                    ArtistsContent(
                        artists = artists,
                        onArtistClick = { navController.navigate("artist_detail/${it.id}") }
                    )
                }
                LibraryTab.GENRES -> {
                    val genres by viewModel.genres.collectAsStateWithLifecycle()
                    GenresContent(
                        genres = genres,
                        onGenreClick = { genre ->
                            scope.launch {
                                val tracks = viewModel.getGenreTracks(genre.name)
                                if (tracks.isNotEmpty()) onPlayQueue(tracks, tracks.first())
                            }
                        }
                    )
                }
                LibraryTab.PLAYLISTS -> {
                    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
                    PlaylistsContent(
                        playlists = playlists,
                        onPlaylistClick = { navController.navigate("playlist/${it.id}") }
                    )
                }
                LibraryTab.FOLDERS -> {
                    val folders by viewModel.folders.collectAsStateWithLifecycle()
                    FoldersContent(
                        folders = folders,
                        onFolderClick = { folder ->
                            scope.launch {
                                val tracks = viewModel.getFolderTracks(folder.path)
                                if (tracks.isNotEmpty()) onPlayQueue(tracks, tracks.first())
                            }
                        }
                    )
                }
            }
        }

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = LocalSkin.current.accentColor
            )
        }
    }
}

@Composable
fun TracksContent(
    viewModel: LibraryViewModel,
    onTrackClick: (Track) -> Unit,
    onLongTrackClick: (Long) -> Unit
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val recentlyAdded by viewModel.recentlyAdded.collectAsStateWithLifecycle()
    val mostPlayed by viewModel.mostPlayed.collectAsStateWithLifecycle()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedTracks by viewModel.selectedTracks.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val showEmptyState = tracks.isEmpty() && recentlyAdded.isEmpty() && mostPlayed.isEmpty() && recentlyPlayed.isEmpty()

    if (showEmptyState) {
        EmptyState(
            icon = Icons.Outlined.MusicNote,
            title = stringResource(R.string.no_tracks_found),
            subtitle = stringResource(R.string.try_different_search_or_scan_library)
        )
        return
    }

    Box {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            if (recentlyAdded.isNotEmpty()) {
                item(key = "recently_added") {
                    QuickAccessSection(
                        title = "Recently Added",
                        tracks = recentlyAdded,
                        onTrackClick = onTrackClick
                        onSeeAll = { /* ⚠️ Navigate to a dedicated "Recently Added" screen */ }
                    )
                }
            }
            if (mostPlayed.isNotEmpty()) {
                item(key = "most_played") {
                    QuickAccessSection(
                        title = "Most Played",
                        tracks = mostPlayed,
                        onTrackClick = onTrackClick
                    )
                }
            }
            if (recentlyPlayed.isNotEmpty()) {
                item(key = "recently_played") {
                    QuickAccessSection(
                        title = "Recently Played",
                        tracks = recentlyPlayed,
                        onTrackClick = onTrackClick
                    )
                }
            }

            if (tracks.isNotEmpty()) {
                item(key = "all_tracks_header") {
                    Text(
                        "All Tracks",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                    )
                }
                items(items = tracks, key = { "track_${it.id}" }) { track ->
                    TrackItem(
                        track = track,
                        isSelected = selectedTracks.contains(track.id),
                        isSelectionMode = isSelectionMode,
                        onClick = {
                            if (isSelectionMode) viewModel.toggleTrackSelection(track.id)
                            else onTrackClick(track)
                        },
                        onLongClick = { onLongTrackClick(track.id) },
                        modifier = Modifier.animateItemPlacement()
                    )
                }
            }
        }
        FastScroller(
            listState = listState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumsContent(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit,
    modifier: Modifier = Modifier
) {
    if (albums.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Album,
            title = stringResource(R.string.no_albums_found),
            subtitle = stringResource(R.string.try_different_search_or_scan_library)
        )
        return
    }

    val gridState = rememberLazyStaggeredGridState()
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(minSize = 160.dp),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 16.dp
    ) {
        items(items = albums, key = { it.id }) { album ->
            AlbumGridItem(
                album = album,
                onClick = { onAlbumClick(album) },
                modifier = Modifier.animateItemPlacement()
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistsContent(
    artists: List<Artist>,
    onArtistClick: (Artist) -> Unit,
    modifier: Modifier = Modifier
) {
    if (artists.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Person,
            title = stringResource(R.string.no_artists_found),
            subtitle = stringResource(R.string.try_different_search_or_scan_library)
        )
        return
    }

    val gridState = rememberLazyGridState()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(items = artists, key = { it.id }) { artist ->
            ArtistGridItem(
                artist = artist,
                onClick = { onArtistClick(artist) },
                modifier = Modifier.animateItemPlacement()
            )
        }
    }
}

@Composable
fun GenresContent(
    genres: List<Genre>,
    onGenreClick: (Genre) -> Unit,
    modifier: Modifier = Modifier
) {
    if (genres.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Category,
            title = stringResource(R.string.no_genres_found),
            subtitle = stringResource(R.string.try_different_search_or_scan_library)
        )
        return
    }
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(items = genres, key = { it.name }) { genre ->
            GenreItem(
                genre = genre,
                onClick = { onGenreClick(genre) },
                modifier = Modifier.animateItemPlacement()
            )
        }
    }
}

@Composable
fun PlaylistsContent(
    playlists: List<Playlist>,
    onPlaylistClick: (Playlist) -> Unit,
    modifier: Modifier = Modifier
) {
    if (playlists.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.PlaylistPlay,
            title = stringResource(R.string.no_playlists_found),
            subtitle = stringResource(R.string.create_playlist_to_get_started)
        )
        return
    }
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(items = playlists, key = { it.id }) { playlist ->
            PlaylistItem(
                playlist = playlist,
                onClick = { onPlaylistClick(playlist) },
                modifier = Modifier.animateItemPlacement()
            )
        }
    }
}

@Composable
fun FoldersContent(
    folders: List<Folder>,
    onFolderClick: (Folder) -> Unit,
    modifier: Modifier = Modifier
) {
    if (folders.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Folder,
            title = stringResource(R.string.no_folders_found),
            subtitle = stringResource(R.string.try_different_search_or_scan_library)
        )
        return
    }
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(items = folders, key = { it.path }) { folder ->
            FolderItem(
                folder = folder,
                onClick = { onFolderClick(folder) },
                modifier = Modifier.animateItemPlacement()
            )
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
    onGenreClick: (Genre) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onShowMore: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier
) {
    if (searchResults.isEmpty) {
        EmptyState(
            icon = Icons.Outlined.SearchOff,
            title = stringResource(R.string.no_results_found),
            subtitle = stringResource(R.string.try_different_search_terms, searchQuery)
        )
        return
    }
    
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        if (searchResults.tracks.isNotEmpty()) {
            item { SearchSectionHeader("Tracks", searchResults.tracks.size) }
            items(searchResults.tracks.take(5)) { track ->
                TrackItem(track = track, onClick = { onTrackClick(track) })
            }
            if (searchResults.tracks.size > 5) {
                item {
                    ShowMoreButton(
                        searchResults.tracks.size - 5,
                        onClick = { onShowMore(LibraryTab.TRACKS) })
                }
            }
        }
        if (searchResults.albums.isNotEmpty()) {
            item { SearchSectionHeader("Albums", searchResults.albums.size) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(searchResults.albums.take(10)) { album ->
                        AlbumGridItem(album, { onAlbumClick(album) }, Modifier.width(140.dp))
                    }
                }
            }
            if (searchResults.albums.size > 10) {
                item {
                    ShowMoreButton(
                        searchResults.albums.size - 10,
                        onClick = { onShowMore(LibraryTab.ALBUMS) })
                }
            }
        }

        if (searchResults.artists.isNotEmpty()) {
            item { SearchSectionHeader("Artists", searchResults.artists.size) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(searchResults.artists.take(10)) { artist ->
                        ArtistGridItem(artist, { onArtistClick(artist) }, Modifier.width(120.dp))
                    }
                }
            }
            if (searchResults.artists.size > 10) {
                item {
                    ShowMoreButton(
                        searchResults.artists.size - 10,
                        onClick = { onShowMore(LibraryTab.ARTISTS) })
                }
            }
        }

        if (searchResults.genres.isNotEmpty()) {
            item { SearchSectionHeader("Genres", searchResults.genres.size) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(searchResults.genres.take(10)) { genre ->
                        GenreGridItem(genre, { onGenreClick(genre) }, Modifier.width(120.dp))
                    }
                }
            }
            if (searchResults.genres.size > 10) {
                item {
                    ShowMoreButton(
                        searchResults.genres.size - 10,
                        onClick = { onShowMore(LibraryTab.GENRES) })
                }
            }
        }

        if (searchResults.playlists.isNotEmpty()) {
            item { SearchSectionHeader("Playlists", searchResults.playlists.size) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(searchResults.playlists.take(10)) { playlist ->
                        PlaylistGridItem(playlist, { onPlaylistClick(playlist) }, Modifier.width(120.dp))
                    }
                }
            }
            if (searchResults.playlists.size > 10) {
                item {
                    ShowMoreButton(
                        searchResults.playlists.size - 10,
                        onClick = { onShowMore(LibraryTab.PLAYLISTS) })
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
    viewModel: LibraryViewModel,
    onShowAddToPlaylistDialog: () -> Unit
) {
    val skin = LocalSkin.current
    val selectedCount by viewModel.selectedTracks.collectAsStateWithLifecycle()
    
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = skin.accentColor.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${selectedCount.size} selected",
                modifier = Modifier.padding(start = 12.dp),
                fontWeight = FontWeight.Medium
            )
            Row {
                IconButton(onClick = viewModel::selectAllTracks) { Icon(Icons.Default.SelectAll, "Select all") }
                IconButton(onClick = onShowAddToPlaylistDialog) { Icon(Icons.Default.PlaylistAdd, "Add to playlist") }
                IconButton(onClick = viewModel::deleteSelectedTracks) { Icon(Icons.Default.Delete, "Delete") }
                IconButton(onClick = viewModel::clearSelection) { Icon(Icons.Default.Clear, "Clear selection") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistDialog(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onAddToExistingPlaylist: (Long) -> Unit,
    onCreateNewPlaylist: (String) -> Unit
) {
    var newPlaylistName by remember { mutableStateOf("") }
    var showNewPlaylistField by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TextButton(onClick = { showNewPlaylistField = !showNewPlaylistField }) {
                    Icon(
                        if (showNewPlaylistField) Icons.Default.Cancel else Icons.Default.Add,
                        null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (showNewPlaylistField) "Cancel" else "New Playlist")
                }
                AnimatedVisibility(showNewPlaylistField) {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Playlist Name") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (newPlaylistName.isNotBlank()) onCreateNewPlaylist(newPlaylistName)
                        }),
                        modifier = Modifier.focusRequester(focusRequester)
                    )
                }
                LaunchedEffect(showNewPlaylistField) {
                    if (showNewPlaylistField) focusRequester.requestFocus()
                }
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                LazyColumn {
                    items(playlists) { playlist ->
                        ListItem(
                            headlineContent = { Text(playlist.name) },
                            supportingContent = { Text("${playlist.trackCount} tracks") },
                            modifier = Modifier.clickable { onAddToExistingPlaylist(playlist.id) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (newPlaylistName.isNotBlank()) onCreateNewPlaylist(newPlaylistName) },
                enabled = newPlaylistName.isNotBlank() && showNewPlaylistField
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
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
fun FastScroller(
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val showScroller by remember {
        derivedStateOf { listState.isScrollInProgress || listState.firstVisibleItemIndex > 10 }
    }
    var handleOffset by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = showScroller,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300)),
        modifier = modifier.fillMaxHeight()
    ) {
        BoxWithConstraints(modifier.alpha(if (isDragging) 1f else 0.8f)) {
            val totalItems = listState.layoutInfo.totalItemsCount
            if (totalItems == 0) return@BoxWithConstraints

            fun scrollToFraction(fraction: Float) {
                val index = (totalItems * fraction).toInt()
                scope.launch { listState.scrollToItem(index.coerceIn(0, totalItems - 1)) }
            }

            val firstVisibleItem = listState.firstVisibleItemIndex.toFloat()
            val visibleItems = listState.layoutInfo.visibleItemsInfo.size.toFloat()
            val thumbHeight = (constraints.maxHeight * (visibleItems / totalItems)).coerceIn(50f, 200f)
            val thumbPosition = (constraints.maxHeight - thumbHeight) * (firstVisibleItem / (totalItems - visibleItems))

            Box(
                modifier = Modifier
                    .offset(y = thumbPosition.dp)
                    .width(16.dp)
                    .height(thumbHeight.dp)
                    .background(LocalSkin.current.accentColor, shape = RoundedCornerShape(8.dp))
            )

            Box(modifier = Modifier
                .fillMaxWidth(0.2f)
                .fillMaxHeight()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isDragging = true
                            handleOffset = it.y
                            scrollToFraction(handleOffset / constraints.maxHeight)
                        },
                        onTap = { isDragging = false },
                    )
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = { isDragging = false },
                        onVerticalDrag = { _, dragAmount ->
                            handleOffset += dragAmount
                            scrollToFraction(handleOffset / constraints.maxHeight)
                        }
                    )
                }
            )
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
