package com.example.data

import kotlinx.coroutines.flow.Flow

class BrowserRepository(private val browserDao: BrowserDao) {

    val allBookmarks: Flow<List<Bookmark>> = browserDao.getAllBookmarks()
    val allHistory: Flow<List<HistoryItem>> = browserDao.getAllHistory()
    val allDownloads: Flow<List<DownloadItem>> = browserDao.getAllDownloads()

    suspend fun insertBookmark(bookmark: Bookmark) {
        browserDao.insertBookmark(bookmark)
    }

    suspend fun deleteBookmark(id: Int) {
        browserDao.deleteBookmark(id)
    }

    suspend fun deleteBookmarkByUrl(url: String) {
        browserDao.deleteBookmarkByUrl(url)
    }

    suspend fun getBookmarkByUrl(url: String): Bookmark? {
        return browserDao.getBookmarkByUrl(url)
    }


    suspend fun insertHistory(title: String, url: String) {
        // Only insert if it doesn't match the last entry or clean url
        if (url.isNotBlank() && !url.startsWith("about:") && !url.startsWith("file:")) {
            browserDao.insertHistory(HistoryItem(title = title, url = url))
        }
    }

    suspend fun deleteHistory(id: Int) {
        browserDao.deleteHistoryItem(id)
    }

    suspend fun clearHistory() {
        browserDao.clearHistory()
    }


    suspend fun insertDownload(downloadItem: DownloadItem): Long {
        return browserDao.insertDownload(downloadItem)
    }

    suspend fun updateDownloadProgress(id: Int, status: String, progress: Int, sizeLabel: String) {
        browserDao.updateDownloadProgress(id, status, progress, sizeLabel)
    }

    suspend fun deleteDownload(id: Int) {
        browserDao.deleteDownload(id)
    }
}
