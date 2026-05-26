package com.example.viewmodel

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BrowserViewModel(val app: Application) : AndroidViewModel(app) {

    private val db = BrowserDatabase.getDatabase(app)
    private val repository = BrowserRepository(db.browserDao())

    // Database UI States
    val bookmarks = repository.allBookmarks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val history = repository.allHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val downloads = repository.allDownloads.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // WebView state variables
    private val _currentUrl = MutableStateFlow("")
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    private val _currentTitle = MutableStateFlow("AdBlock Web Browser")
    val currentTitle: StateFlow<String> = _currentTitle.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadingProgress = MutableStateFlow(0)
    val loadingProgress: StateFlow<Int> = _loadingProgress.asStateFlow()

    private val _blockedAdsCount = MutableStateFlow(0)
    val blockedAdsCount: StateFlow<Int> = _blockedAdsCount.asStateFlow()

    private val _adBlockEnabled = MutableStateFlow(true)
    val adBlockEnabled: StateFlow<Boolean> = _adBlockEnabled.asStateFlow()

    private val _isBookmarked = MutableStateFlow(false)
    val isBookmarked: StateFlow<Boolean> = _isBookmarked.asStateFlow()

    // Full screen Overlay State (For Hardware Acceleration Videos)
    private val _isFullscreenActive = MutableStateFlow(false)
    val isFullscreenActive: StateFlow<Boolean> = _isFullscreenActive.asStateFlow()

    init {
        // Automatically check bookmarks state whenever url changes
        viewModelScope.launch {
            _currentUrl.collect { url ->
                if (url.isNotEmpty() && !url.startsWith("file://") && !url.startsWith("about:")) {
                    val bookmark = repository.getBookmarkByUrl(url)
                    _isBookmarked.value = bookmark != null
                } else {
                    _isBookmarked.value = false
                }
            }
        }
    }

    fun setUrl(url: String) {
        if (_currentUrl.value != url) {
            _currentUrl.value = url
        }
    }

    fun setTitle(title: String) {
        val cleanTitle = title.trim()
        if (cleanTitle.isNotEmpty() && cleanTitle != "about:blank" && cleanTitle != "Home") {
            _currentTitle.value = cleanTitle
            viewModelScope.launch {
                val url = _currentUrl.value
                if (url.isNotEmpty() && !url.contains("google_ads") && !url.startsWith("file:")) {
                    repository.insertHistory(cleanTitle, url)
                }
            }
        }
    }

    fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }

    fun setProgress(progress: Int) {
        _loadingProgress.value = progress
    }

    fun incrementBlockedAds() {
        _blockedAdsCount.value += 1
    }

    fun resetBlockedAds() {
        _blockedAdsCount.value = 0
    }

    fun toggleAdBlock() {
        _adBlockEnabled.value = !_adBlockEnabled.value
        // Reload current page if toggle switched to apply changes
    }

    fun toggleBookmark() {
        val url = _currentUrl.value
        val title = _currentTitle.value
        if (url.isEmpty() || url.startsWith("file:") || url.startsWith("about:")) return

        viewModelScope.launch {
            val bookmark = repository.getBookmarkByUrl(url)
            if (bookmark != null) {
                repository.deleteBookmark(bookmark.id)
                _isBookmarked.value = false
            } else {
                repository.insertBookmark(Bookmark(title = if (title.isEmpty()) url else title, url = url))
                _isBookmarked.value = true
            }
        }
    }

    fun deleteBookmark(id: Int) {
        viewModelScope.launch {
            repository.deleteBookmark(id)
            // recalculate bookmark state
            val bookmark = repository.getBookmarkByUrl(_currentUrl.value)
            _isBookmarked.value = bookmark != null
        }
    }

    fun deleteHistoryItem(id: Int) {
        viewModelScope.launch {
            repository.deleteHistory(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun triggerSearchOrLoad(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return

        val targetUrl = if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
            trimmed
        } else if (trimmed.contains(".") && !trimmed.contains(" ") && trimmed.length > 3) {
            "https://$trimmed"
        } else {
            "https://www.google.com/search?q=${Uri.encode(trimmed)}"
        }
        resetBlockedAds()
        setUrl(targetUrl)
    }

    fun handleDownload(url: String?, userAgent: String?, contentDisposition: String?, mimetype: String?) {
        val safeUrl = url ?: return
        viewModelScope.launch {
            val fileName = URLUtil.guessFileName(safeUrl, contentDisposition, mimetype)
            val filePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath + "/" + fileName
            
            val rowId = repository.insertDownload(
                DownloadItem(
                    fileName = fileName,
                    url = safeUrl,
                    filePath = filePath,
                    progress = 100, // Complete file scheduling status
                    sizeLabel = "Queued in Downloads",
                    status = "Completed"
                )
            )

            try {
                val context = app
                val request = DownloadManager.Request(Uri.parse(safeUrl)).apply {
                    mimetype?.let { setMimeType(it) }
                    userAgent?.let { addRequestHeader("User-Agent", it) }
                    setDescription("Downloading file from Web Browser")
                    setTitle(fileName)
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                }

                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Downloading: $fileName", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                repository.updateDownloadProgress(rowId.toInt(), "Failed", 0, e.localizedMessage ?: "Failed to start")
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, "Download failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun setFullscreen(active: Boolean) {
        _isFullscreenActive.value = active
    }
}
