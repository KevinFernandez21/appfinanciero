package com.kevinfernandez.appfinanciero.backup

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kevinfernandez.appfinanciero.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinanceBackupServiceTest {
    private lateinit var source: FinanceDatabase
    private lateinit var target: FinanceDatabase

    @Before fun open() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        source = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build()
        target = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java).build()
    }
    @After fun close() { source.close(); target.close() }

    private suspend fun seed() {
        val dao = source.financeDao()
        dao.insertAccount(AccountEntity("cash", "Cash", 9_007_199_254_740_993L, "USD"))
        dao.insertAccount(AccountEntity("bank", "Bank", 0, "USD"))
        dao.insertCategory(CategoryEntity("food", "Food"))
        dao.insertTransaction(TransactionEntity("transfer", "TRANSFER", "cash", "bank", 101, 20_000, null, null, "USD"))
        dao.insertTransaction(TransactionEntity("expense", "EXPENSE", "cash", null, 10, 20_001, "food", "Private note", "USD"))
        dao.insertGoal(GoalEntity("goal", "Emergency", 10_000, "USD", 21_000))
        dao.insertReservation(GoalReservationEntity("reserve", "goal", "cash", 500, 20_000))
        dao.insertReservationChange(ReservationChangeEntity(42, "reserve", 500, 20_000))
        dao.insertBudget(BudgetEntity("budget", "food", 200, "USD", 20_000, 20_006))
        dao.insertWeeklyReview(WeeklyReviewEntity("review", 20_000, 20_006, "USD", 0, 10, "Private review"))
    }

    @Test fun allTablesRoundTripWithExactLongsAndNulls() = runBlocking {
        seed()
        val json = FinanceBackupService(source).exportJson()
        val service = FinanceBackupService(target)
        assertEquals(10, service.previewJson(json).recordCount)
        service.restoreJson(json, confirmed = true)
        assertEquals(source.financeDao().getSnapshot(), target.financeDao().getSnapshot())
        assertEquals(source.financeDao().getReservationChanges(), target.financeDao().getReservationChanges())
        assertEquals(json, service.exportJson())
    }

    @Test fun exportsVersionTwoAndRestoresLegacyVersionOneWithoutCorrections() = runBlocking {
        seed()
        val exported = JSONObject(FinanceBackupService(source).exportJson())
        assertEquals(2, exported.getInt("formatVersion"))
        assertEquals(2, exported.getInt("databaseVersion"))
        assertEquals(0, exported.getJSONObject("tables").getJSONArray("transaction_corrections").length())
        val service = FinanceBackupService(target)
        rejected { service.restoreJson(JSONObject(exported.toString()).put("databaseVersion", 1).toString(), true) }
        rejected { service.restoreJson(JSONObject(exported.toString()).put("formatVersion", 1).put("databaseVersion", 1).toString(), true) }
        val legacy = exported.put("formatVersion", 1).put("databaseVersion", 1).apply {
            getJSONObject("tables").remove("transaction_corrections")
        }.toString()
        assertEquals(10, service.previewJson(legacy).recordCount)
        service.restoreJson(legacy, true)
        assertEquals(source.financeDao().getSnapshot(), target.financeDao().getSnapshot())
    }

    @Test fun voidedTransactionAndCorrectionRoundTripWithoutChangingEffectiveBalance() = runBlocking {
        seed()
        source.financeDao().insertCorrection(TransactionCorrectionEntity("void-expense", "expense", null, "Duplicate entry", 20_002))
        val json = FinanceBackupService(source).exportJson()
        val service = FinanceBackupService(target)
        assertEquals(1, service.previewJson(json).tableCounts["transaction_corrections"])
        service.restoreJson(json, true)
        assertEquals(source.financeDao().getSnapshot(), target.financeDao().getSnapshot())
        assertEquals(source.financeDao().getCorrections(), target.financeDao().getCorrections())
        assertEquals(2, target.financeDao().getTransactions().size)
        assertEquals(source.financeDao().getSnapshot().ledger().balance("cash"), target.financeDao().getSnapshot().ledger().balance("cash"))
        assertEquals(json, service.exportJson())
    }

    @Test fun invalidCorrectionReferencesDuplicatesAndCyclesRejectWithoutWriting() = runBlocking {
        seed()
        source.financeDao().insertCorrection(TransactionCorrectionEntity("void-expense", "expense", null, "Duplicate entry", 20_002))
        val original = FinanceBackupService(source).exportJson()
        val service = FinanceBackupService(target)
        val mutations: List<(JSONObject) -> Unit> = listOf(
            { it.put("originalTransactionId", "missing") },
            { it.put("replacementTransactionId", "missing") },
            { it.put("replacementTransactionId", "expense") },
            { it.put("reason", " ") },
        )
        mutations.forEach { mutate ->
            val malformed = JSONObject(original).apply {
                mutate(getJSONObject("tables").getJSONArray("transaction_corrections").getJSONObject(0))
            }.toString()
            rejected { service.restoreJson(malformed, true) }
        }
        val duplicate = JSONObject(original).apply {
            val rows = getJSONObject("tables").getJSONArray("transaction_corrections")
            rows.put(JSONObject(rows.getJSONObject(0).toString()).put("id", "second-void"))
        }.toString()
        rejected { service.restoreJson(duplicate, true) }
        val duplicateId = JSONObject(original).apply {
            val rows = getJSONObject("tables").getJSONArray("transaction_corrections")
            rows.put(JSONObject(rows.getJSONObject(0).toString()).put("originalTransactionId", "transfer"))
        }.toString()
        rejected { service.restoreJson(duplicateId, true) }
        val cycle = JSONObject(original).apply {
            val rows = getJSONObject("tables").getJSONArray("transaction_corrections")
            rows.getJSONObject(0).put("replacementTransactionId", "transfer")
            rows.put(JSONObject().put("id", "void-transfer").put("originalTransactionId", "transfer")
                .put("replacementTransactionId", "expense").put("reason", "Correction").put("dateEpochDay", "20003"))
        }.toString()
        rejected { service.restoreJson(cycle, true) }
        assertEquals(FinanceSnapshot(), target.financeDao().getSnapshot())
    }

    @Test fun releasedReservationPreservesZeroTotalAndCompleteAuditHistory() = runBlocking {
        seed()
        source.financeDao().updateReservation(source.financeDao().getReservations().single().copy(amountMinor = 0))
        source.financeDao().insertReservationChange(ReservationChangeEntity(43, "reserve", 0, 20_001))
        val json = FinanceBackupService(source).exportJson()
        val service = FinanceBackupService(target)
        assertEquals(11, service.previewJson(json).recordCount)
        service.restoreJson(json, confirmed = true)
        assertEquals(0L, target.financeDao().getReservations().single().amountMinor)
        assertEquals(listOf(500L, 0L), target.financeDao().getReservationChanges().map { it.amountMinor })
        assertEquals(source.financeDao().getSnapshot(), target.financeDao().getSnapshot())
        assertEquals(source.financeDao().getReservationChanges(), target.financeDao().getReservationChanges())
        assertEquals(json, service.exportJson())
    }

    @Test fun negativeReservationOrChangeIsRejectedWithoutWriting() = runBlocking {
        seed()
        val original = FinanceBackupService(source).exportJson()
        val service = FinanceBackupService(target)
        for (table in listOf("goal_reservations", "reservation_changes")) {
            val negative = JSONObject(original).apply {
                getJSONObject("tables").getJSONArray(table).getJSONObject(0).put("amountMinor", "-1")
            }.toString()
            rejected { service.restoreJson(negative, true) }
            assertEquals(FinanceSnapshot(), target.financeDao().getSnapshot())
            assertTrue(target.financeDao().getReservationChanges().isEmpty())
        }
    }

    @Test fun confirmationAndNonemptyTargetRejectWithoutOverwriting() = runBlocking {
        seed()
        val json = FinanceBackupService(source).exportJson()
        val service = FinanceBackupService(target)
        rejected { service.restoreJson(json, confirmed = false) }
        assertTrue(target.financeDao().getAccounts().isEmpty())
        target.financeDao().insertAccount(AccountEntity("existing", "Preserved", 321, "USD"))
        rejected { service.restoreJson(json, confirmed = true) }
        assertEquals(listOf(AccountEntity("existing", "Preserved", 321, "USD")), target.financeDao().getAccounts())
        assertTrue(target.financeDao().getTransactions().isEmpty())
    }

    @Test fun invalidForeignKeyVersionAndLossyNumberLeaveTargetEmpty() = runBlocking {
        seed()
        val original = FinanceBackupService(source).exportJson()
        val service = FinanceBackupService(target)
        val version = JSONObject(original).put("formatVersion", 999).toString()
        rejected { service.restoreJson(version, true) }
        val foreignKey = JSONObject(original).apply {
            getJSONObject("tables").getJSONArray("transactions").getJSONObject(0).put("sourceAccountId", "missing")
        }.toString()
        rejected { service.restoreJson(foreignKey, true) }
        val lossy = JSONObject(original).apply {
            getJSONObject("tables").getJSONArray("accounts").getJSONObject(0).put("openingBalanceMinor", 1.25)
        }.toString()
        rejected { service.restoreJson(lossy, true) }
        assertEquals(FinanceSnapshot(), target.financeDao().getSnapshot())
        assertTrue(target.financeDao().getReservationChanges().isEmpty())
    }

    @Test fun failureDuringInsertRollsBackEveryTable() = runBlocking {
        seed()
        source.financeDao().insertCorrection(TransactionCorrectionEntity("void-expense", "expense", null, "Duplicate entry", 20_002))
        val json = FinanceBackupService(source).exportJson()
        target.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_budget BEFORE INSERT ON budgets BEGIN SELECT RAISE(ABORT, 'test failure'); END",
        )
        try { FinanceBackupService(target).restoreJson(json, true); fail("Insert failure expected") }
        catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(FinanceSnapshot(), target.financeDao().getSnapshot())
        assertTrue(target.financeDao().getReservationChanges().isEmpty())
        assertTrue(target.financeDao().getCorrections().isEmpty())
    }

    @Test fun malformedDuplicateAndOverflowingBackupAreRejected() = runBlocking {
        seed()
        val original = FinanceBackupService(source).exportJson()
        val service = FinanceBackupService(target)
        rejected { service.previewJson("not JSON") }
        rejected { service.previewJson(original + " trailing") }
        val duplicate = JSONObject(original).apply {
            val accounts = getJSONObject("tables").getJSONArray("accounts")
            accounts.put(accounts.getJSONObject(0))
        }.toString()
        rejected { service.restoreJson(duplicate, true) }
        val overflow = JSONObject(original).apply {
            getJSONObject("tables").getJSONArray("accounts").getJSONObject(0).put("openingBalanceMinor", Long.MAX_VALUE.toString())
            getJSONObject("tables").getJSONArray("transactions").getJSONObject(0)
                .put("kind", "INCOME").put("destinationAccountId", JSONObject.NULL)
        }.toString()
        rejected { service.restoreJson(overflow, true) }
        assertEquals(FinanceSnapshot(), target.financeDao().getSnapshot())
    }

    private suspend fun rejected(action: suspend () -> Unit) {
        try { action(); fail("Invalid or unsafe restore must be rejected") }
        catch (_: IllegalArgumentException) { }
    }
}
