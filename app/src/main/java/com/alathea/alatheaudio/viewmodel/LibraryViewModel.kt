package com.alathea.alatheaudio.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alathea.alatheaudio.data.model.*
import com.alathea.alatheaudio.repository.LibraryRepository
import com.alathea.alatheaudio.repository.PlaylistRepository
import com.alathea.alatheaudio.repository.SearchRepository
import com.alathea.alatheaudio.service.MediaScannerService
import com.alathea.alatheaudio.utils.SortOrder
import com.alathea.alatheaudio.utils.FilterType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val playlistRepository: PlaylistRepository,
    private val searchRepository: SearchRepository,
    private val mediaScannerService: MediaScannerService
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedLibraryTab = MutableStateFlow(LibraryTab.TRACKS)
    val selectedLibraryTab: StateFlow<LibraryTab> = _selectedLibraryTab.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.TITLE_ASC)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _filterType = MutableStateFlow(FilterType.ALL)
    val filterType: StateFlow<FilterType> = _filterType.asStateFlow()

    private val _scanProgress = MutableStateFlow(0f)
    val scanProgress: StateFlow<Float> = _scanProgress.asStateFlow()

    private val _isScanningLibrary = MutableStateFlow(false)
    val isScanningLibrary: StateFlow<Boolean> = _isScanningLibrary.asStateFlow()

    private val _scanStatusMessage = MutableStateFlow("")
    val scanStatusMessage: StateFlow<String> = _scanStatusMessage.asStateFlow()

    private val _libraryStats = MutableStateFlow(LibraryStats())
    val libraryStats: StateFlow<LibraryStats> = _libraryStats.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _selectedTracks = MutableStateFlow<Set<Long>>(emptySet())
    val selectedTracks: StateFlow<Set<Long>> = _selectedTracks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val debouncedSearch = searchQuery
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.length >= 2) {
                searchRepository.searchAll(query, 200)
            } else {
                flowOf(SearchResult.empty())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SearchResult.empty()
        )

    val searchResults: StateFlow<SearchResult> = debouncedSearch

    val tracks: StateFlow<List<Track>> = combine(
        selectedLibraryTab,
        sortOrder,
        filterType,
        searchQuery
    ) { tab, sort, filter, query ->
        if (tab == LibraryTab.TRACKS) {
            Triple(sort, filter, query)
        } else {
            Triple(SortOrder.TITLE_ASC, FilterType.ALL, "")
        }
    }.flatMapLatest { (sort, filter, query) ->
        if (query.isNotEmpty() && query.length >= 2) {
            searchRepository.searchTracks(query, sort, filter)
        } else {
            libraryRepository.getAllTracks(sort, filter)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val albums: StateFlow<List<Album>> = combine(
        selectedLibraryTab,
        sortOrder,
        searchQuery
    ) { tab, sort, query ->
        if (tab == LibraryTab.ALBUMS) {
            Pair(sort, query)
        } else {
            Pair(SortOrder.TITLE_ASC, "")
        }
    }.flatMapLatest { (sort, query) ->
        if (query.isNotEmpty() && query.length >= 2) {
            searchRepository.searchAlbums(query, sort)
        } else {
            libraryRepository.getAllAlbums(sort)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val artists: StateFlow<List<Artist>> = combine(
        selectedLibraryTab,
        sortOrder,
        searchQuery
    ) { tab, sort, query ->
        if (tab == LibraryTab.ARTISTS) {
            Pair(sort, query)
        } else {
            Pair(SortOrder.TITLE_ASC, "")
        }
    }.flatMapLatest { (sort, query) ->
        if (query.isNotEmpty() && query.length >= 2) {
            searchRepository.searchArtists(query, sort)
        } else {
            libraryRepository.getAllArtists(sort)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val genres: StateFlow<List<Genre>> = combine(
        selectedLibraryTab,
        sortOrder,
        searchQuery
    ) { tab, sort, query ->
        if (tab == LibraryTab.GENRES) {
            Pair(sort, query)
        } else {
            Pair(SortOrder.TITLE_ASC, "")
        }
    }.flatMapLatest { (sort, query) ->
        if (query.isNotEmpty() && query.length >= 2) {
            searchRepository.searchGenres(query, sort)
        } else {
            libraryRepository.getAllGenres(sort)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val playlists: StateFlow<List<Playlist>> = combine(
        selectedLibraryTab,
        sortOrder,
        searchQuery
    ) { tab, sort, query ->
        if (tab == LibraryTab.PLAYLISTS) {
            Pair(sort, query)
        } else {
            Pair(SortOrder.TITLE_ASC, "")
        }
    }.flatMapLatest { (sort, query) ->
        if (query.isNotEmpty() && query.length >= 2) {
            playlistRepository.searchPlaylists(query, sort)
        } else {
            playlistRepository.getAllPlaylists(sort)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val folders: StateFlow<List<Folder>> = combine(
        selectedLibraryTab,
        sortOrder,
        searchQuery
    ) { tab, sort, query ->
        if (tab == LibraryTab.FOLDERS) {
            Pair(sort, query)
        } else {
            Pair(SortOrder.TITLE_ASC, "")
        }
    }.flatMapLatest { (sort, query) ->
        libraryRepository.getFolderHierarchy(sort, query)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val recentlyAdded: StateFlow<List<Track>> = libraryRepository.getRecentlyAddedTracks(50)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val mostPlayed: StateFlow<List<Track>> = libraryRepository.getMostPlayedTracks(50)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val recentlyPlayed: StateFlow<List<Track>> = libraryRepository.getRecentlyPlayedTracks(50)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var scanJob: Job? = null

    init {
        updateLibraryStats()

        viewModelScope.launch {
            mediaScannerService.scanProgress.collect { progress ->
                _scanProgress.value = progress
            }
        }
        
        viewModelScope.launch {
            mediaScannerService.isScanningLibrary.collect { isScanning ->
                _isScanningLibrary.value = isScanning
            }
        }
        
        viewModelScope.launch {
            mediaScannerService.scanStatusMessage.collect { message ->
                _scanStatusMessage.value = message
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query.trim()
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    fun selectLibraryTab(tab: LibraryTab) {
        _selectedLibraryTab.value = tab
        clearSelection()
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun setFilterType(filter: FilterType) {
        _filterType.value = filter
    }

    fun startMediaScan() {
        if (_isScanningLibrary.value) return
        
        scanJob = viewModelScope.launch {
            try {
                mediaScannerService.startFullScan()
                updateLibraryStats()
            } catch (e: Exception) {
                _scanStatusMessage.value = "Scan failed: ${e.message}"
            }
        }
    }

    fun startIncrementalScan() {
        if (_isScanningLibrary.value) return
        
        scanJob = viewModelScope.launch {
            try {
                mediaScannerService.startIncrementalScan()
                updateLibraryStats()
            } catch (e: Exception) {
                _scanStatusMessage.value = "Incremental scan failed: ${e.message}"
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        mediaScannerService.cancelScan()
    }

    fun toggleSelectionMode() {
        _isSelectionMode.value = !_isSelectionMode.value
        if (!_isSelectionMode.value) {
            _selectedTracks.value = emptySet()
        }
    }

    fun toggleTrackSelection(trackId: Long) {
        val currentSelection = _selectedTracks.value.toMutableSet()
        if (currentSelection.contains(trackId)) {
            currentSelection.remove(trackId)
        } else {
            currentSelection.add(trackId)
        }
        _selectedTracks.value = currentSelection
    }

    fun selectAllTracks() {
        val allTrackIds = tracks.value.map { it.id }.toSet()
        _selectedTracks.value = allTrackIds
    }

    fun clearSelection() {
        _selectedTracks.value = emptySet()
        _isSelectionMode.value = false
    }

    fun addSelectedToPlaylist(playlistId: Long) {
        viewModelScope.launch {
            val trackIds = _selectedTracks.value.toList()
            playlistRepository.addTracksToPlaylist(playlistId, trackIds)
            clearSelection()
        }
    }

    fun createPlaylistFromSelected(name: String) {
        viewModelScope.launch {
            val trackIds = _selectedTracks.value.toList()
            playlistRepository.createPlaylist(name, trackIds)
            clearSelection()
        }
    }

    fun deleteSelectedTracks() {
        viewModelScope.launch {
            val trackIds = _selectedTracks.value.toList()
            libraryRepository.deleteTracks(trackIds)
            clearSelection()
            updateLibraryStats()
        }
    }

    suspend fun getAlbumTracks(albumId: Long): List<Track> {
        return withContext(Dispatchers.IO) {
            libraryRepository.getTracksByAlbum(albumId)
        }
    }

    suspend fun getArtistTracks(artistId: Long): List<Track> {
        return withContext(Dispatchers.IO) {
            libraryRepository.getTracksByArtist(artistId)
        }
    }

    suspend fun getGenreTracks(genre: String): List<Track> {
        return withContext(Dispatchers.IO) {
            libraryRepository.getTracksByGenre(genre)
        }
    }

    suspend fun getFolderTracks(folderPath: String): List<Track> {
        return withContext(Dispatchers.IO) {
            libraryRepository.getTracksByFolder(folderPath)
        }
    }

    fun playAlbum(albumId: Long) {
        viewModelScope.launch {
            val tracks = getAlbumTracks(albumId)
        }
    }

    fun playArtist(artistId: Long, shuffled: Boolean = false) {
        viewModelScope.launch {
            val tracks = getArtistTracks(artistId)
        }
    }

    fun shuffleAll() {
        viewModelScope.launch {
            val allTracks = tracks.value
            if (allTracks.isNotEmpty()) {/* Managed by event system */}
        }
    }

    fun refreshTrack(trackId: Long) {
        viewModelScope.launch {
            libraryRepository.refreshTrackMetadata(trackId)
        }
    }

    fun deleteTrack(trackId: Long) {
        viewModelScope.launch {
            libraryRepository.deleteTracks(listOf(trackId))
            updateLibraryStats()
        }
    }

    private fun updateLibraryStats() {
        viewModelScope.launch {
            val stats = libraryRepository.getLibraryStats()
            _libraryStats.value = stats
        }
    }

    fun preloadAlbumArt(tracks: List<Track>) {
        viewModelScope.launch(Dispatchers.IO) {
            tracks.forEach { track ->
                libraryRepository.preloadAlbumArt(track.albumArtUri)
            }
        }
    }

    fun warmUpCache() {
        viewModelScope.launch(Dispatchers.IO) {
            libraryRepository.warmUpCache()
        }
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
    }
}

enum class LibraryTab {
    TRACKS, ALBUMS, ARTISTS, GENRES, PLAYLISTS, FOLDERS
}

data class LibraryStats(
    val totalTracks: Int = 0,
    val totalAlbums: Int = 0,
    val totalArtists: Int = 0,
    val totalGenres: Int = 0,
    val totalPlaylists: Int = 0,
    val totalDuration: Long = 0,
    val totalSize: Long = 0,
    val averageBitrate: Int = 0,
    val highResTrackCount: Int = 0,
    val formatBreakdown: Map<String, Int> = emptyMap(),
    val lastScanDate: Long = 0
)

data class SearchResult(
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val playlists: List<Playlist> = emptyList()
) {
    companion object {
        fun empty() = SearchResult()
    }
    
    val isEmpty: Boolean
        get() = tracks.isEmpty() && albums.isEmpty() && artists.isEmpty() && 
                genres.isEmpty() && playlists.isEmpty()
}
