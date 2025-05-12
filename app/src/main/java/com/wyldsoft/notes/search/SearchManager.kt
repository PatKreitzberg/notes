package com.wyldsoft.notes.search

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wyldsoft.notes.views.PageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SearchResult represents a single search result match
 * @param text The matching text
 * @param startIndex The start index in the original text
 * @param endIndex The end index in the original text
 * @param yPosition Approximate y-position in the page (for scrolling to the result)
 */
data class SearchResult(
    val text: String,
    val startIndex: Int,
    val endIndex: Int,
    val yPosition: Float
)

/**
 * Manages the search functionality for notes
 */
class SearchManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    // Search state
    var isSearchVisible by mutableStateOf(false)
    var isSearching by mutableStateOf(false)

    // Search results
    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    // Current result index
    var currentResultIndex by mutableStateOf(0)

    // Recognized text from HTR
    private var recognizedText = ""
    private var lastSearchTerm = ""

    /**
     * Search for the given query in the page
     * @param query The search query
     * @param page The page view to search in
     * @return The list of search results
     */
    suspend fun search(query: String, page: PageView): List<SearchResult> {
        if (query.isBlank()) return emptyList()

        isSearching = true
        lastSearchTerm = query
        currentResultIndex = 0

        try {
            // Run HTR on the entire page
            recognizedText = page.recognizeText()

            // Find all matches (case-insensitive)
            val results = findMatches(query, recognizedText)

            // Update results
            _searchResults.value = results

            // Set current result to first match if any
            if (results.isNotEmpty()) {
                currentResultIndex = 1
            }

            return results
        } finally {
            isSearching = false
        }
    }

    /**
     * Move to the next search result
     * @return The new current result index
     */
    fun nextResult(): Int {
        val results = _searchResults.value
        if (results.isEmpty()) return 0

        currentResultIndex = if (currentResultIndex < results.size) {
            currentResultIndex + 1
        } else {
            1 // Loop back to the first result
        }

        return currentResultIndex
    }

    /**
     * Move to the previous search result
     * @return The new current result index
     */
    fun previousResult(): Int {
        val results = _searchResults.value
        if (results.isEmpty()) return 0

        currentResultIndex = if (currentResultIndex > 1) {
            currentResultIndex - 1
        } else {
            results.size // Loop to the last result
        }

        return currentResultIndex
    }

    /**
     * Get the current search result
     * @return The current search result or null if no results
     */
    fun getCurrentResult(): SearchResult? {
        val results = _searchResults.value
        if (results.isEmpty() || currentResultIndex <= 0 || currentResultIndex > results.size) {
            return null
        }

        return results[currentResultIndex - 1]
    }

    /**
     * Clear search results
     */
    fun clearResults() {
        _searchResults.value = emptyList()
        currentResultIndex = 0
        recognizedText = ""
        lastSearchTerm = ""
    }

    /**
     * Find all matches of the query in the text
     * @param query The search query
     * @param text The text to search in
     * @return List of search results
     */
    private fun findMatches(query: String, text: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val queryLower = query.lowercase()
        val textLower = text.lowercase()

        var index = 0
        while (index != -1 && index < textLower.length) {
            index = textLower.indexOf(queryLower, index)
            if (index != -1) {
                // Calculate approximate y-position based on line height
                // This is a very rough estimate - in a real implementation,
                // you'd need to map the text position to actual page coordinates
                val linesBeforeMatch = text.substring(0, index).count { it == '\n' }
                val approximateYPosition = linesBeforeMatch * 40f  // Assuming 40px line height

                results.add(
                    SearchResult(
                        text = text.substring(index, index + query.length),
                        startIndex = index,
                        endIndex = index + query.length,
                        yPosition = approximateYPosition
                    )
                )

                index += query.length
            }
        }

        return results
    }
}