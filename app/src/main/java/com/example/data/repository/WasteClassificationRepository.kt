package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.WasteCategory
import com.example.data.model.WasteClassification
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class WasteClassificationRepository(
    private val db: FirebaseFirestore,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun userClassificationsRef(userId: String) =
        db.collection("users").document(userId).collection("classifications")

    fun observeClassifications(userId: String): Flow<List<WasteClassification>> {
        val path = "users/$userId/classifications"
        return userClassificationsRef(userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    WasteClassification::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                val currentUid = try {
                    Firebase.auth.currentUser?.uid
                } catch (_: Exception) {
                    null
                }
                if (exceptionToLog != null && currentUid == userId) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun saveClassification(
        userId: String,
        classification: WasteClassification,
    ): Result<String> {
        val docRef = userClassificationsRef(userId).document()
        val safeCategory = WasteCategory.fromLabel(classification.category).label
        val safeItemName = classification.itemName.trim().ifBlank { "$safeCategory Waste Item" }.take(120)
        val safeConfidence = classification.confidence.coerceIn(0.0, 1.0)
        val safeDisposal = classification.disposalMethod.trim()
            .ifBlank { WasteCategory.fromLabel(safeCategory).sortingGuide }
            .take(500)
        val safeBin = classification.binColor.trim()
            .ifBlank { WasteCategory.fromLabel(safeCategory).defaultBin }
            .take(80)
        val safeEcoTip = classification.ecoTip.trim().take(300)
        val safeSource = if (classification.sourceType == "Camera") "Camera" else "Upload"

        val payload = mapOf(
            "userId" to userId,
            "category" to safeCategory,
            "itemName" to safeItemName,
            "confidence" to safeConfidence,
            "isUncertain" to classification.isUncertain,
            "disposalMethod" to safeDisposal,
            "binColor" to safeBin,
            "ecoTip" to safeEcoTip,
            "sourceType" to safeSource,
            "createdAt" to FieldValue.serverTimestamp(),
        )

        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateCategory(
        userId: String,
        classificationId: String,
        newCategory: WasteCategory,
    ): Result<Unit> {
        require(classificationId.isNotBlank()) { "Classification ID cannot be blank" }
        val docRef = userClassificationsRef(userId).document(classificationId)
        val updates = mapOf(
            "category" to newCategory.label,
            "binColor" to newCategory.defaultBin,
            "disposalMethod" to newCategory.sortingGuide,
            "ecoTip" to newCategory.ecoFact,
            "isUncertain" to false,
        )
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteClassification(
        userId: String,
        classificationId: String,
    ): Result<Unit> {
        require(classificationId.isNotBlank()) { "Classification ID cannot be blank" }
        val docRef = userClassificationsRef(userId).document(classificationId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }
}
