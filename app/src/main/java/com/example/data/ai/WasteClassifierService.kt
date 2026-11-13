package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.WasteCategory
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class WastePrediction(
    val category: WasteCategory,
    val itemName: String,
    val confidence: Double,
    val isUncertain: Boolean,
    val disposalMethod: String,
    val binColor: String,
    val ecoTip: String,
)

interface WasteClassifier {
    suspend fun classify(bitmap: Bitmap): Result<WastePrediction>
}

class FirebaseAiWasteClassifier : WasteClassifier {

    private val responseSchema = Schema.obj(
        properties = mapOf(
            "category" to Schema.enumeration(
                values = listOf("Plastic", "Paper", "Metal", "Glass", "Organic", "Other"),
                description = "Primary waste category for the item in the image"
            ),
            "itemName" to Schema.string(
                description = "Short descriptive name of the detected waste item (e.g., PET Plastic Bottle, Aluminum Soda Can, Banana Peel)"
            ),
            "confidence" to Schema.double(
                description = "Confidence score from 0.0 to 1.0 indicating certainty in the classification. Use lower scores (<0.65) if the image is blurry, dark, ambiguous, or does not clearly show waste."
            ),
            "disposalMethod" to Schema.string(
                description = "Actionable step-by-step disposal and preparation instruction for the user."
            ),
            "binColor" to Schema.string(
                description = "Recommended bin label (e.g., Blue Recycling Bin, Paper & Cardboard Bin, Yellow Metal Bin, Glass Bank, Green Compost Bin, or General Waste)."
            ),
            "ecoTip" to Schema.string(
                description = "Concise recycling or sustainability tip specific to this item."
            )
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = this@FirebaseAiWasteClassifier.responseSchema
                temperature = 0.2f
            }
        )
    }

    override suspend fun classify(bitmap: Bitmap): Result<WastePrediction> {
        val scaledBitmap = scaleBitmapForInference(bitmap, maxDimension = 1024)
        val promptText = """
            You are an expert smart waste classification and recycling vision system.
            Analyze the provided image and classify the primary waste item into exactly one of:
            - Plastic (bottles, containers, wrappers, polymer cups)
            - Paper (newspapers, cardboard, office sheets, paperboard)
            - Metal (aluminum cans, tin cans, metal scraps, foil)
            - Glass (glass bottles, jars)
            - Organic (food scraps, vegetable peels, yard/plant waste)
            - Other (composite, electronic, ceramic, hazardous, or unrecognized non-waste scene)

            If the image is blurry, dark, occluded, or does not clearly show a waste object, assign "Other" or the closest guess with a confidence below 0.60.
            Return clean structured JSON matching the schema.
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        image(scaledBitmap)
                        text(promptText)
                    }
                )
            }
            val rawJson = response.text
                ?: return Result.failure(IllegalStateException("AI model returned an empty response."))

            val json = JSONObject(rawJson)
            val categoryLabel = json.optString("category", "Other")
            val category = WasteCategory.fromLabel(categoryLabel)
            val confidence = json.optDouble("confidence", 0.0).coerceIn(0.0, 1.0)
            val itemName = json.optString("itemName", "${category.label} Item")
                .trim()
                .ifBlank { "${category.label} Item" }
                .take(120)
            val disposalMethod = json.optString("disposalMethod", category.sortingGuide)
                .trim()
                .ifBlank { category.sortingGuide }
                .take(500)
            val binColor = json.optString("binColor", category.defaultBin)
                .trim()
                .ifBlank { category.defaultBin }
                .take(80)
            val ecoTip = json.optString("ecoTip", category.ecoFact)
                .trim()
                .ifBlank { category.ecoFact }
                .take(300)

            Result.success(
                WastePrediction(
                    category = category,
                    itemName = itemName,
                    confidence = confidence,
                    isUncertain = confidence < UNCERTAINTY_THRESHOLD,
                    disposalMethod = disposalMethod,
                    binColor = binColor,
                    ecoTip = ecoTip,
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Waste classification timed out", e)
            Result.failure(
                IllegalStateException("Classification timed out after 60 seconds. Check your connection and try again.")
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI waste classification failed", e)
            Result.failure(e)
        }
    }

    private fun scaleBitmapForInference(source: Bitmap, maxDimension: Int): Bitmap {
        val width = source.width
        val height = source.height
        if (width <= maxDimension && height <= maxDimension) return source
        val scale = maxDimension.toFloat() / maxOf(width, height).toFloat()
        val targetWidth = (width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }

    companion object {
        private const val TAG = "WasteClassifier"
        const val UNCERTAINTY_THRESHOLD = 0.65
    }
}
