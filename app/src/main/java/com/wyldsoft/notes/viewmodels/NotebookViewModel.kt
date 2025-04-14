package com.wyldsoft.notes.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wyldsoft.notes.data.entity.Notebook
import com.wyldsoft.notes.data.entity.PageType
import com.wyldsoft.notes.data.relation.NotebookWithPages
import com.wyldsoft.notes.data.repository.NotebookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for notebook-related operations.
 *
 * Provides a reactive interface for working with notebooks.
 *
 * @property notebookRepository Repository for notebook data access
 */
class NotebookViewModel(private val notebookRepository: NotebookRepository) : ViewModel() {

    // UI state for notebooks list
    private val _uiState = MutableStateFlow<NotebookListUiState>(NotebookListUiState.Loading)
    val uiState: StateFlow<NotebookListUiState> = _uiState.asStateFlow()

    // Currently selected notebook ID
    private val _selectedNotebookId = MutableStateFlow<String?>(null)
    val selectedNotebookId: StateFlow<String?> = _selectedNotebookId.asStateFlow()

    // Notebooks with their pages
    val notebooksWithPages: Flow<List<NotebookWithPages>> = notebookRepository.getAllNotebooksWithPages()

    init {
        loadNotebooks()
    }

    /**
     * Load all notebooks from the repository.
     */
    fun loadNotebooks() {
        viewModelScope.launch {
            try {
                _uiState.value = NotebookListUiState.Loading
                // We don't need to collect the flow here, just set the state to Success
                // since we're exposing the flow directly via notebooksWithPages
                _uiState.value = NotebookListUiState.Success
            } catch (e: Exception) {
                _uiState.value = NotebookListUiState.Error(e.message ?: "Unknown error loading notebooks")
            }
        }
    }

    /**
     * Create a new notebook.
     *
     * @param title Title of the notebook
     * @param pageType Type of pages in the notebook
     * @param coverColor Color of the notebook cover
     * @return ID of the created notebook
     */
    suspend fun createNotebook(
        title: String,
        pageType: PageType = PageType.A4,
        coverColor: Int = 0xFF1E88E5.toInt()
    ): String {
        return notebookRepository.createNotebook(title, pageType, coverColor)
    }

    /**
     * Update a notebook.
     *
     * @param notebook The notebook to update
     */
    suspend fun updateNotebook(notebook: Notebook) {
        notebookRepository.updateNotebook(notebook)
    }

    /**
     * Update the title of a notebook.
     *
     * @param notebookId ID of the notebook to update
     * @param title New title
     */
    suspend fun updateNotebookTitle(notebookId: String, title: String) {
        notebookRepository.updateNotebookTitle(notebookId, title)
    }

    /**
     * Delete a notebook.
     *
     * @param notebookId ID of the notebook to delete
     */
    suspend fun deleteNotebook(notebookId: String) {
        notebookRepository.deleteNotebook(notebookId)
        if (_selectedNotebookId.value == notebookId) {
            _selectedNotebookId.value = null
        }
    }

    /**
     * Select a notebook.
     *
     * @param notebookId ID of the notebook to select
     */
    fun selectNotebook(notebookId: String) {
        _selectedNotebookId.value = notebookId
    }

    /**
     * Clear notebook selection.
     */
    fun clearSelection() {
        _selectedNotebookId.value = null
    }

    /**
     * Get the page count for a specific notebook.
     *
     * @param notebookId ID of the notebook
     * @return The number of pages in the notebook
     */
    suspend fun getPageCount(notebookId: String): Int {
        return notebookRepository.getPageCount(notebookId)
    }

    /**
     * Factory for creating NotebookViewModel instances.
     *
     * @property repository Repository for notebook data access
     */
    class Factory(private val repository: NotebookRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(NotebookViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return NotebookViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

/**
 * UI state for the notebook list screen.
 */
sealed class NotebookListUiState {
    /**
     * Loading state when notebooks are being fetched.
     */
    object Loading : NotebookListUiState()

    /**
     * Success state when notebooks are loaded successfully.
     */
    object Success : NotebookListUiState()

    /**
     * Error state when loading notebooks fails.
     *
     * @property message Error message
     */
    data class Error(val message: String) : NotebookListUiState()
}