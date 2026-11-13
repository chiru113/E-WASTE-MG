package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.WasteCategory
import com.example.data.model.WasteClassification
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WasteClassificationRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun saveClassification_authenticatedOwner_createsAndObservesDocument() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = WasteClassificationRepository(firestore)

        val record = WasteClassification(
            userId = uid,
            category = WasteCategory.PLASTIC.label,
            itemName = "Clear PET Water Bottle",
            confidence = 0.93,
            isUncertain = false,
            disposalMethod = WasteCategory.PLASTIC.sortingGuide,
            binColor = WasteCategory.PLASTIC.defaultBin,
            ecoTip = WasteCategory.PLASTIC.ecoFact,
            sourceType = "Camera",
        )

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveClassification(uid, record)
        }
        assertTrue("Expected saveClassification to succeed", createResult.isSuccess)
        val docId = createResult.getOrThrow()
        assertTrue(docId.isNotBlank())

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeClassifications(uid).first { list -> list.any { it.id == docId } }
        }
        val saved = observed.first { it.id == docId }
        assertEquals("Plastic", saved.category)
        assertEquals("Clear PET Water Bottle", saved.itemName)
    }

    @Test
    fun updateAndDeleteClassification_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = WasteClassificationRepository(firestore)

        val docId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveClassification(
                uid,
                WasteClassification(
                    userId = uid,
                    category = WasteCategory.OTHER.label,
                    itemName = "Crushed Jar",
                    confidence = 0.58,
                    isUncertain = true,
                    disposalMethod = WasteCategory.OTHER.sortingGuide,
                    binColor = WasteCategory.OTHER.defaultBin,
                    ecoTip = WasteCategory.OTHER.ecoFact,
                    sourceType = "Upload",
                )
            ).getOrThrow()
        }

        val updateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateCategory(uid, docId, WasteCategory.GLASS)
        }
        assertTrue("Expected updateCategory to succeed", updateResult.isSuccess)

        val deleteResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.deleteClassification(uid, docId)
        }
        assertTrue("Expected deleteClassification to succeed", deleteResult.isSuccess)
    }

    @Test
    fun observeClassifications_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = WasteClassificationRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.saveClassification(
                aliceUid,
                WasteClassification(
                    userId = aliceUid,
                    category = WasteCategory.METAL.label,
                    itemName = "Aluminum Soda Can",
                    confidence = 0.91,
                    isUncertain = false,
                    disposalMethod = WasteCategory.METAL.sortingGuide,
                    binColor = WasteCategory.METAL.defaultBin,
                    ecoTip = WasteCategory.METAL.ecoFact,
                    sourceType = "Camera",
                )
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = WasteClassificationRepository(firestore)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeClassifications(aliceUid).first()
            }
        } catch (e: Exception) {
            caught = e
        }

        val firestoreException = checkNotNull(caught?.unwrapFirestoreException()) {
            "Expected nested FirebaseFirestoreException, got $caught"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    @Test
    fun observeClassifications_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = WasteClassificationRepository(firestore)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeClassifications("some_uid").first()
            }
        } catch (e: Exception) {
            caught = e
        }

        val firestoreException = checkNotNull(caught?.unwrapFirestoreException()) {
            "Expected nested FirebaseFirestoreException, got $caught"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@test.com"
        const val BOB_EMAIL = "bob@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
