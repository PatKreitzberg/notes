// app/src/main/java/com/wyldsoft/notes/components/SearchComponent.kt
package com.wyldsoft.notes.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.foundation.layout.size
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * A search component that appears as a dropdown below the toolbar
 * @param isVisible Whether the search component is visible
 * @param onClose Called when the search component is closed
 * @param onSearch Called when a search is initiated with the query
 * @param onNext Called when the next button is pressed
 * @param onPrevious Called when the previous button is pressed
 * @param isSearching Whether a search is currently in progress
 * @param resultsCount The number of search results found
 * @param currentResult The index of the current search result (1-based)
 */
@Composable
fun SearchComponent(
    isVisible: Boolean,
    onClose: () -> Unit,
    onSearch: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    isSearching: Boolean = false,
    resultsCount: Int = 0,
    currentResult: Int = 0
) {
    var searchQuery by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colors.surface)
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search input field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search in note") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search icon"
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (searchQuery.isNotEmpty()) {
                                onSearch(searchQuery)
                                focusManager.clearFocus()
                            }
                        }
                    ),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = MaterialTheme.colors.primary,
                        unfocusedBorderColor = Color.Gray
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Search button
                Button(
                    onClick = {
                        if (searchQuery.isNotEmpty()) {
                            onSearch(searchQuery)
                            focusManager.clearFocus()
                        }
                    },
                    enabled = searchQuery.isNotEmpty() && !isSearching
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text("Search")
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Close button
                IconButton(
                    onClick = {
                        onClose()
                        searchQuery = ""
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close search"
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Results navigation row
            if (resultsCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Results counter
                    Text(
                        text = "$currentResult of $resultsCount matches",
                        color = MaterialTheme.colors.primary
                    )

                    // Navigation buttons
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous button
                        IconButton(
                            onClick = onPrevious,
                            enabled = resultsCount > 1 && currentResult > 1
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Previous result",
                                tint = if (resultsCount > 1 && currentResult > 1)
                                    MaterialTheme.colors.primary else Color.Gray
                            )
                        }

                        // Next button
                        IconButton(
                            onClick = onNext,
                            enabled = resultsCount > 1 && currentResult < resultsCount
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "Next result",
                                tint = if (resultsCount > 1 && currentResult < resultsCount)
                                    MaterialTheme.colors.primary else Color.Gray
                            )
                        }
                    }
                }
            } else if (!isSearching && searchQuery.isNotEmpty()) {
                // No results message
                Text(
                    text = "No matches found",
                    color = Color.Gray,
                    modifier = Modifier.padding(4.dp)
                )
            }
        }
    }
}