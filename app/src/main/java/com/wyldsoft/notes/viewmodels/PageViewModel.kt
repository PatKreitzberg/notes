package com.wyldsoft.notes.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wyldsoft.notes.data.entity.Page
import com.wyldsoft.notes.data.repository.PageRepository
import com.wyldsoft.notes.data.repository.StrokeRepository
import com.wyldsoft.notes.utils.Stroke
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for page-related operations.
 *
 * Provides a reactive interface for working with pages and their strokes.
 *
 * @property pageRepository Repository for page data access
 * @property strokeRepository Repository for stroke data access
 */
class PageViewModel(
    private val pageRepository: PageRepository,
    private val strokeRepository: StrokeRepository
) : ViewModel() {

    // UI state for page view
    private val _uiState = MutableStateFlow<PageUiState>(PageUiState.Loading)
    val uiState: StateFlow<PageUiState> = _uiState.asStateFlow()

    // Current page ID
    private val _currentPageId = MutableStateFlow<String?>(null)
    val currentPageId: StateFlow<String?> = _currentPageId.asStateFlow()

    // Current notebook ID
    private val _currentNotebookId = MutableStateFlow<String?>(null)
    val currentNotebookId: StateFlow<String?> = _currentNotebookId.asStateFlow()

    /**
     * Load a page by its ID.
     *
     * @param pageId ID of the page to load
     */
    fun loadPage(pageId: String) {
        viewModelScope.launch {
            try {
                _uiState.value = PageUiState.Loading
                val page = pageRepository.getPageById(pageId)
                if (page != null) {
                    _currentPageId.value = pageId
                    _currentNotebookId.value = page.notebookId
                    _uiState.value = PageUiState.Success(page)
                } else {
                    _uiState.value = PageUiState.Error("Page not found")
                }
            } catch (e: Exception) {
                _uiState.value = PageUiState.Error(e.message ?: "Unknown error loading page")
            }
        }
    }

    /**
     * Get all pages for a specific notebook.
     *
     * @param notebookId ID of the notebook
     * @return Flow of pages in the notebook
     */
    fun getPagesForNotebook(notebookId: String): Flow<List<Page>> {
        return pageRepository.getPagesForNotebookFlow(notebookId)
    }

    /**
     * Create a new page in a notebook.
     *
     * @param notebookId ID of the notebook
     * @param width Width of the page
     * @param height Height of the page
     * @return ID of the created page
     */
    suspend fun createPage(notebookId: String, width: Int, height: Int): String {
        val pageId = pageRepository.createPage(notebookId, width, height)
        _currentPageId.value = pageId
        _currentNotebookId.value = notebookId
        loadPage(pageId)
        return pageId
    }

    /**
     * Delete a page.
     *
     * @param pageId ID of the page to delete
     */
    suspend fun deletePage(pageId: String) {
        pageRepository.deletePage(pageId)
        if (_currentPageId.value == pageId) {
            _currentPageId.value = null
            _uiState.value = PageUiState.Closed
        }
    }

    /**
     * Get all strokes for the current page.
     *
     * @return List of strokes
     */
    suspend fun getStrokesForCurrentPage(): List<Stroke> {
        val pageId = _currentPageId.value ?: return emptyList()
        return strokeRepository.getDomainStrokesForPage(pageId)
    }

    /**
     * Add a stroke to the current page.
     *
     * @param stroke The stroke to add
     */
    suspend fun addStroke(stroke: Stroke) {
        strokeRepository.addStroke(stroke)
        pageRepository.updateLastModifiedAt(stroke.pageId)
    }

    /**
     * Add multiple strokes to the current page.
     *
     * @param strokes The list of strokes to add
     */
    suspend fun addStrokes(strokes: List<Stroke>) {
        if (strokes.isEmpty()) return
        strokeRepository.addStrokes(strokes)
        val pageId = strokes.first().pageId
        pageRepository.updateLastModifiedAt(pageId)
    }

    /**
     * Delete strokes from the current page.
     *
     * @param strokeIds IDs of the strokes to delete
     */
    suspend fun deleteStrokes(strokeIds: List<String>) {
        strokeRepository.deleteStrokes(strokeIds)
    }

    /**
     * Move to the next page in the notebook.
     *
     * @return true if moved to next page, false if already at the last page
     */
    suspend fun moveToNextPage(): Boolean {
        val currentPage = getCurrentPage() ?: return false
        val notebookId = currentPage.notebookId
        val pages = pageRepository.getPagesForNotebook(notebookId)

        val currentIndex = pages.indexOfFirst { it.id == currentPage.id }
        if (currentIndex < pages.size - 1) {
            val nextPage = pages[currentIndex + 1]
            loadPage(nextPage.id)
            return true
        }
        return false
    }

    /**
     * Move to the previous page in the notebook.
     *
     * @return true if moved to previous page, false if already at the first page
     */
    suspend fun moveToPreviousPage(): Boolean {
        val currentPage = getCurrentPage() ?: return false
        val notebookId = currentPage.notebookId
        val pages = pageRepository.getPagesForNotebook(notebookId)

        val currentIndex = pages.indexOfFirst { it.id == currentPage.id }
        if (currentIndex > 0) {
            val previousPage = pages[currentIndex - 1]
            loadPage(previousPage.id)
            return true
        }
        return false
    }

    /**
     * Get the current page.
     *
     * @return The current page or null if no page is loaded
     */
    private suspend fun getCurrentPage(): Page? {
        val pageId = _currentPageId.value ?: return null
        return pageRepository.getPageById(pageId)
    }

    /**
     * Factory for creating PageViewModel instances.
     *
     * @property pageRepository Repository for page data access
     * @property strokeRepository Repository for stroke data access
     */
    class Factory(
        private val pageRepository: PageRepository,
        private val strokeRepository: StrokeRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PageViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return PageViewModel(pageRepository, strokeRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

/**
 * UI state for the page view.
 */
sealed class PageUiState {
    /**
     * Loading state when page is being fetched.
     */
    object Loading : PageUiState()

    /**
     * Success state when page is loaded successfully.
     *
     * @property page The loaded page
     */
    data class Success(val page: Page) : PageUiState()

    /**
     * Error state when loading page fails.
     *
     * @property message Error message
     */
    data class Error(val message: String) : PageUiState()

    /**
     * Closed state when no page is open.
     */
    object Closed : PageUiState()
}