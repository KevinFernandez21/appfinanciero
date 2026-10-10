package com.kevinfernandez.appfinanciero.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.kevinfernandez.appfinanciero.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.assertEquals

class WeeklyReviewUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = ViewModelStore()
    private lateinit var db: FinanceDatabase
    private lateinit var model: FinanceViewModel

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build()
        runBlocking { db.financeDao().insertAccount(AccountEntity("cash", "Cash", 50_000, "USD")) }
        compose.runOnIdle { model = FinanceViewModel(db, context); store.put("finance", model) }
    }
    @After fun cleanup() { compose.runOnIdle { store.clear() } }
    private fun openReview() {
        compose.waitUntil(10_000) { model.snapshot.value != null }
        compose.onNodeWithText("Review last 7 days · USD").performScrollTo().performClick()
    }

    @Test fun cancelDraftWritesNothing() {
        compose.setContent { MaterialTheme { FinanceScreen(model) } }
        openReview()
        compose.onNodeWithText("Reflection note (optional)").performTextInput("A private reflection")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Reflection note (optional)").assertDoesNotExist()
        assertEquals(0, runBlocking { RoomFinanceRepository(db).snapshot().weeklyReviews.size })
    }

    @Test fun restoredDraftRequiresExplicitSaveAndDisplaysHistoricalTotals() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MaterialTheme { FinanceScreen(model) } }
        openReview()
        compose.onNodeWithText("Reflection note (optional)").performTextInput("No movements recorded")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("No movements recorded").assertIsDisplayed()
        assertEquals(0, runBlocking { RoomFinanceRepository(db).snapshot().weeklyReviews.size })
        compose.onNodeWithText("Save review").performClick()
        compose.waitUntil(10_000) { model.snapshot.value?.weeklyReviews?.size == 1 && !model.busy.value }
        val row = runBlocking { RoomFinanceRepository(db).snapshot().weeklyReviews.single() }
        assertEquals("No movements recorded", row.note)
        assertEquals(0L, row.incomeMinor)
        assertEquals(0L, row.expensesMinor)
        compose.onNodeWithText("Recorded at review time · USD").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Reflection note (optional)").assertDoesNotExist()
    }
}
