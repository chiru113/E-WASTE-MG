package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.WasteCategory
import com.example.data.model.WasteClassification
import com.example.ui.components.CategoryBadge
import com.example.ui.components.EmptyWasteState
import com.example.ui.theme.EcoMintAccent
import com.example.ui.theme.EcoWarningAmber
import com.example.ui.viewmodel.HistoryUiState
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    historyState: HistoryUiState,
    selectedCategory: WasteCategory?,
    searchQuery: String,
    onCategoryFilterSelected: (WasteCategory?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onUpdateCategory: (String, WasteCategory) -> Unit,
    onDeleteItem: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingItem by remember { mutableStateOf<WasteClassification?>(null) }
    var deletingItem by remember { mutableStateOf<WasteClassification?>(null) }
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Classification History",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Synced waste scans with confidence & disposal notes",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search item, category, or bin…") },
                leadingIcon = {
                    Icon(imageVector = Icons.Filled.Search, contentDescription = "Search history")
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("history_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { onCategoryFilterSelected(null) },
                    label = { Text("All") }
                )
                WasteCategory.entries.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = {
                            onCategoryFilterSelected(
                                if (selectedCategory == category) null else category
                            )
                        },
                        label = { Text(category.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (historyState) {
                is HistoryUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is HistoryUiState.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = historyState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                is HistoryUiState.Success -> {
                    val filteredItems = remember(historyState.items, selectedCategory, searchQuery) {
                        val query = searchQuery.trim().lowercase()
                        historyState.items.filter { item ->
                            val matchesCat = selectedCategory == null || item.wasteCategory == selectedCategory
                            val matchesQuery = query.isBlank() ||
                                item.itemName.lowercase().contains(query) ||
                                item.category.lowercase().contains(query) ||
                                item.binColor.lowercase().contains(query)
                            matchesCat && matchesQuery
                        }
                    }

                    if (filteredItems.isEmpty()) {
                        EmptyWasteState(
                            title = if (historyState.items.isEmpty()) "No Scans Recorded Yet" else "No Matching Records",
                            subtitle = if (historyState.items.isEmpty()) {
                                "Classify your first waste item in the Scanner tab to build your database history."
                            } else {
                                "Try clearing your category filter or search term."
                            }
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredItems, key = { it.id }) { item ->
                                HistoryRecordCard(
                                    item = item,
                                    formattedDate = item.createdAt?.toDate()?.let { dateFormatter.format(it) } ?: "Just now",
                                    onEdit = { editingItem = item },
                                    onDelete = { deletingItem = item }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    editingItem?.let { item ->
        ModalBottomSheet(onDismissRequest = { editingItem = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Reclassify \"${item.itemName}\"",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Select the verified waste category to update this record and analytics.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WasteCategory.entries.forEach { option ->
                        FilterChip(
                            selected = option == item.wasteCategory,
                            onClick = {
                                onUpdateCategory(item.id, option)
                                editingItem = null
                            },
                            label = { Text(option.label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    deletingItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deletingItem = null },
            title = { Text("Delete Record?") },
            text = { Text("Remove \"${item.itemName}\" (${item.category}) from your classification history?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteItem(item.id)
                        deletingItem = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingItem = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun HistoryRecordCard(
    item: WasteClassification,
    formattedDate: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val category = item.wasteCategory
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_item_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryBadge(category = category)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (item.isUncertain) EcoWarningAmber.copy(alpha = 0.16f) else EcoMintAccent.copy(alpha = 0.16f),
                        shape = CircleShape
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (item.isUncertain) {
                                Icon(
                                    imageVector = Icons.Filled.WarningAmber,
                                    contentDescription = "Uncertain",
                                    tint = EcoWarningAmber,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = "${item.confidencePercent}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (item.isUncertain) EcoWarningAmber else EcoMintAccent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Text(
                text = item.itemName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = item.disposalMethod,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (item.sourceType == "Camera") Icons.Filled.CameraAlt else Icons.Filled.PhotoLibrary,
                        contentDescription = item.sourceType,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${item.sourceType} • $formattedDate",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit category",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Delete record",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
