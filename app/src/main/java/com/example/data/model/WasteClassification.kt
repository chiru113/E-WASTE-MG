package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class WasteClassification(
    @DocumentId val id: String = "",
    val userId: String = "",
    val category: String = WasteCategory.OTHER.label,
    val itemName: String = "",
    val confidence: Double = 0.0,
    val isUncertain: Boolean = false,
    val disposalMethod: String = "",
    val binColor: String = "",
    val ecoTip: String = "",
    val sourceType: String = "Upload",
    @ServerTimestamp val createdAt: Timestamp? = null,
) {
    val wasteCategory: WasteCategory
        get() = WasteCategory.fromLabel(category)

    val confidencePercent: Int
        get() = (confidence.coerceIn(0.0, 1.0) * 100).toInt()
}
