package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.WasteClassifier
import com.example.data.ai.WastePrediction
import com.example.data.model.WasteCategory
import com.example.data.model.WasteClassification
import com.example.data.repository.WasteClassificationRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    data class Success(val items: List<WasteClassification>) : HistoryUiState
    data class Error(val message: String) : HistoryUiState
}

sealed interface ClassifyUiState {
    data object Idle : ClassifyUiState
    data class Analyzing(val preview: Bitmap, val sourceType: String) : ClassifyUiState
    data class Result(
        val preview: Bitmap,
        val prediction: WastePrediction,
        val sourceType: String,
        val savedDocId: String?,
        val saveWarning: String? = null,
    ) : ClassifyUiState
    data class Error(val preview: Bitmap?, val message: String) : ClassifyUiState
}

data class WasteAnalyticsSummary(
    val totalScans: Int = 0,
    val averageConfidencePercent: Int = 0,
    val recyclingRatePercent: Int = 0,
    val uncertainCount: Int = 0,
    val cameraCount: Int = 0,
    val uploadCount: Int = 0,
    val topCategory: WasteCategory? = null,
    val categoryCounts: Map<WasteCategory, Int> = emptyMap(),
)

class MainWasteViewModel(
    private val repository: WasteClassificationRepository,
    private val classifier: WasteClassifier,
    private val currentUserId: String,
) : ViewModel() {

    private val _classifyState = MutableStateFlow<ClassifyUiState>(ClassifyUiState.Idle)
    val classifyState: StateFlow<ClassifyUiState> = _classifyState.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow<WasteCategory?>(null)
    val selectedCategoryFilter: StateFlow<WasteCategory?> = _selectedCategoryFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    val historyState: StateFlow<HistoryUiState> = repository.observeClassifications(currentUserId)
        .map<List<WasteClassification>, HistoryUiState> { items ->
            HistoryUiState.Success(items)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing classification history", rootError)
            emit(HistoryUiState.Error(rootError.message ?: "Failed to load classification history."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = HistoryUiState.Loading
        )

    fun classifyImage(bitmap: Bitmap, sourceType: String) {
        viewModelScope.launch {
            _classifyState.value = ClassifyUiState.Analyzing(bitmap, sourceType)
            val predictionResult = classifier.classify(bitmap)
            predictionResult.fold(
                onSuccess = { prediction ->
                    val record = WasteClassification(
                        userId = currentUserId,
                        category = prediction.category.label,
                        itemName = prediction.itemName,
                        confidence = prediction.confidence,
                        isUncertain = prediction.isUncertain,
                        disposalMethod = prediction.disposalMethod,
                        binColor = prediction.binColor,
                        ecoTip = prediction.ecoTip,
                        sourceType = sourceType,
                    )
                    val saveResult = repository.saveClassification(currentUserId, record)
                    _classifyState.value = ClassifyUiState.Result(
                        preview = bitmap,
                        prediction = prediction,
                        sourceType = sourceType,
                        savedDocId = saveResult.getOrNull(),
                        saveWarning = saveResult.exceptionOrNull()?.localizedMessage
                            ?.let { "Classified, but failed to save to cloud: $it" }
                    )
                },
                onFailure = { error ->
                    _classifyState.value = ClassifyUiState.Error(
                        preview = bitmap,
                        message = error.localizedMessage ?: "Unable to classify this waste image."
                    )
                }
            )
        }
    }

    fun overrideCurrentResultCategory(newCategory: WasteCategory) {
        val current = _classifyState.value as? ClassifyUiState.Result ?: return
        val updatedPrediction = current.prediction.copy(
            category = newCategory,
            isUncertain = false,
            binColor = newCategory.defaultBin,
            disposalMethod = newCategory.sortingGuide,
            ecoTip = newCategory.ecoFact,
        )
        _classifyState.value = current.copy(prediction = updatedPrediction)

        val docId = current.savedDocId ?: return
        viewModelScope.launch {
            repository.updateCategory(currentUserId, docId, newCategory).fold(
                onSuccess = { _actionMessage.value = "Updated category to ${newCategory.label}" },
                onFailure = { err -> _actionMessage.value = err.localizedMessage ?: "Update failed" }
            )
        }
    }

    fun updateHistoryCategory(classificationId: String, newCategory: WasteCategory) {
        viewModelScope.launch {
            repository.updateCategory(currentUserId, classificationId, newCategory).fold(
                onSuccess = { _actionMessage.value = "Corrected to ${newCategory.label}" },
                onFailure = { err -> _actionMessage.value = err.localizedMessage ?: "Failed to update record" }
            )
        }
    }

    fun deleteHistoryItem(classificationId: String) {
        viewModelScope.launch {
            repository.deleteClassification(currentUserId, classificationId).fold(
                onSuccess = { _actionMessage.value = "Record deleted" },
                onFailure = { err -> _actionMessage.value = err.localizedMessage ?: "Failed to delete record" }
            )
        }
    }

    fun resetClassification() {
        _classifyState.value = ClassifyUiState.Idle
    }

    fun setCategoryFilter(category: WasteCategory?) {
        _selectedCategoryFilter.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun computeAnalytics(items: List<WasteClassification>): WasteAnalyticsSummary {
        if (items.isEmpty()) return WasteAnalyticsSummary()
        val counts = WasteCategory.entries.associateWith { cat ->
            items.count { it.wasteCategory == cat }
        }
        val avgConf = (items.map { it.confidence }.average() * 100).toInt()
        val recyclableOrCompostable = items.count { it.wasteCategory != WasteCategory.OTHER }
        val recyclingRate = (recyclableOrCompostable * 100) / items.size
        val uncertainCount = items.count { it.isUncertain }
        val cameraCount = items.count { it.sourceType == "Camera" }
        val uploadCount = items.size - cameraCount
        val topCat = counts.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key
        return WasteAnalyticsSummary(
            totalScans = items.size,
            averageConfidencePercent = avgConf,
            recyclingRatePercent = recyclingRate,
            uncertainCount = uncertainCount,
            cameraCount = cameraCount,
            uploadCount = uploadCount,
            topCategory = topCat,
            categoryCounts = counts,
        )
    }

    fun buildTextReport(items: List<WasteClassification>): String {
        val summary = computeAnalytics(items)
        val sb = StringBuilder()
        sb.appendLine("=== EcoSort AI — Waste Classification Report ===")
        sb.appendLine("Total Classified Items: ${summary.totalScans}")
        sb.appendLine("Average Model Confidence: ${summary.averageConfidencePercent}%")
        sb.appendLine("Recoverable / Diverted Rate: ${summary.recyclingRatePercent}%")
        sb.appendLine("Low-Confidence (Uncertain) Scans: ${summary.uncertainCount}")
        sb.appendLine("Capture Sources: ${summary.cameraCount} Camera, ${summary.uploadCount} Upload")
        sb.appendLine()
        sb.appendLine("--- Category Breakdown ---")
        WasteCategory.entries.forEach { cat ->
            val count = summary.categoryCounts[cat] ?: 0
            val pct = if (summary.totalScans > 0) (count * 100) / summary.totalScans else 0
            sb.appendLine("${cat.label}: $count ($pct%)")
        }
        sb.appendLine()
        sb.appendLine("--- Recent Classifications ---")
        items.take(20).forEachIndexed { idx, item ->
            sb.appendLine("${idx + 1}. ${item.itemName} -> ${item.category} (${item.confidencePercent}%) [${item.sourceType}]")
        }
        return sb.toString()
    }

    private companion object {
        const val TAG = "MainWasteVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
