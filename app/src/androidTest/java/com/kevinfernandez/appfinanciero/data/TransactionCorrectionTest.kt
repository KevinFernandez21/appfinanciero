package com.kevinfernandez.appfinanciero.data

import androidx.room.Room
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kevinfernandez.appfinanciero.domain.Account
import com.kevinfernandez.appfinanciero.domain.GoalReservation
import com.kevinfernandez.appfinanciero.domain.Money
import com.kevinfernandez.appfinanciero.domain.SavingsGoal
import com.kevinfernandez.appfinanciero.domain.Transaction
import com.kevinfernandez.appfinanciero.domain.TransactionType
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionCorrectionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun voidingExpenseKeepsOriginalButUpdatesBalanceAndCashFlow() = runBlocking {
        withDatabase { db ->
            val repository = RoomFinanceRepository(db)
            repository.addAccount(Account("cash", "Cash", Money(1_000, "USD")))
            repository.addCategory("food", "Food")
            repository.addTransaction(Transaction("wrong", TransactionType.EXPENSE, "cash", Money(250, "USD"), LocalDate.ofEpochDay(20_000), categoryId = "food", note = "Original detail"))
            repository.voidTransaction("wrong", "Duplicate entry", LocalDate.ofEpochDay(20_001))
            val snapshot = repository.snapshot()
            assertEquals(1, snapshot.transactions.size)
            assertEquals("Original detail", snapshot.transactions.single().note)
            assertEquals(0, snapshot.ledger().transactions.size)
            assertEquals(1_000L, snapshot.ledger().balance("cash").minorUnits)
            assertEquals(0L, snapshot.ledger().cashFlow("USD").expenses.minorUnits)
            assertEquals("wrong", snapshot.corrections.single().originalTransactionId)
            assertEquals("Duplicate entry", snapshot.corrections.single().reason)
            assertEquals(20_001L, snapshot.corrections.single().dateEpochDay)
            repository.addTransaction(Transaction("replacement", TransactionType.EXPENSE, "cash", Money(100, "USD"), LocalDate.ofEpochDay(20_000), categoryId = "food"))
            assertEquals(2, repository.snapshot().transactions.size)
            assertEquals(900L, repository.snapshot().ledger().balance("cash").minorUnits)
        }
    }

    @Test
    fun doubleVoidUnknownIdAndBlankReasonAreRejectedWithoutWriting() = runBlocking {
        withDatabase { db ->
            val repository = RoomFinanceRepository(db)
            repository.addAccount(Account("cash", "Cash", Money(0, "USD")))
            repository.addTransaction(Transaction("income", TransactionType.INCOME, "cash", Money(100, "USD"), LocalDate.ofEpochDay(20_000)))
            rejects<IllegalArgumentException> { repository.voidTransaction("income", " ") }
            rejects<IllegalArgumentException> { repository.voidTransaction("missing", "Mistake") }
            assertEquals(0, db.financeDao().getCorrections().size)
            repository.voidTransaction("income", "Incorrect income")
            rejects<IllegalArgumentException> { repository.voidTransaction("income", "Second attempt") }
            assertEquals(1, db.financeDao().getCorrections().size)
            assertEquals(1, repository.snapshot().transactions.size)
        }
    }

    @Test
    fun voidRejectsBalanceOverflowAndRollsBackCorrection() = runBlocking {
        withDatabase { db ->
            val repository = RoomFinanceRepository(db)
            repository.addAccount(Account("cash", "Cash", Money(Long.MAX_VALUE, "USD")))
            repository.addCategory("food", "Food")
            repository.addTransaction(Transaction("expense", TransactionType.EXPENSE, "cash", Money(1, "USD"), LocalDate.ofEpochDay(20_000), categoryId = "food"))
            repository.addTransaction(Transaction("income", TransactionType.INCOME, "cash", Money(1, "USD"), LocalDate.ofEpochDay(20_001)))
            rejects<ArithmeticException> { repository.voidTransaction("expense", "Incorrect expense") }
            assertEquals(0, db.financeDao().getCorrections().size)
            assertEquals(2, repository.snapshot().ledger().transactions.size)
            assertEquals(Long.MAX_VALUE, repository.snapshot().ledger().balance("cash").minorUnits)
        }
    }

    @Test
    fun voidAllowsHistoricalUnderfundingButRejectsAvailableBalanceOverflow() = runBlocking {
        withDatabase { db ->
            val repository = RoomFinanceRepository(db)
            repository.addAccount(Account("cash", "Cash", Money(1, "USD")))
            repository.addGoal(SavingsGoal("goal", "Goal", Money(1, "USD"), LocalDate.ofEpochDay(21_000)))
            repository.addReservation("reservation", GoalReservation("goal", "cash", Money(1, "USD")))
            repository.addTransaction(Transaction("income", TransactionType.INCOME, "cash", Money(2, "USD"), LocalDate.ofEpochDay(20_000)))
            repository.addCategory("food", "Food")
            repository.addTransaction(Transaction("expense", TransactionType.EXPENSE, "cash", Money(2, "USD"), LocalDate.ofEpochDay(20_001), categoryId = "food"))
            repository.voidTransaction("income", "Income was not received")
            assertEquals(-2L, repository.snapshot().planner().availableBalance("cash").minorUnits)
        }
        withDatabase { db ->
            val repository = RoomFinanceRepository(db)
            repository.addAccount(Account("a", "A", Money(1, "USD")))
            repository.addAccount(Account("b", "B", Money(Long.MIN_VALUE, "USD")))
            repository.addGoal(SavingsGoal("goal", "Goal", Money(1, "USD"), LocalDate.ofEpochDay(21_000)))
            repository.addReservation("reservation", GoalReservation("goal", "a", Money(1, "USD")))
            repository.addTransaction(Transaction("large", TransactionType.TRANSFER, "a", Money(Long.MAX_VALUE, "USD"), LocalDate.ofEpochDay(20_000), destinationAccountId = "b"))
            repository.addTransaction(Transaction("back", TransactionType.TRANSFER, "b", Money(2, "USD"), LocalDate.ofEpochDay(20_001), destinationAccountId = "a"))
            repository.addTransaction(Transaction("out", TransactionType.TRANSFER, "a", Money(2, "USD"), LocalDate.ofEpochDay(20_002), destinationAccountId = "b"))
            rejects<ArithmeticException> { repository.voidTransaction("back", "Wrong direction") }
            assertEquals(0, db.financeDao().getCorrections().size)
            assertEquals(3, repository.snapshot().transactions.size)
        }
    }

    @Test
    fun correctionAndOriginalSurviveReopening() = runBlocking {
        val context = instrumentation.targetContext
        val name = "finance-correction-${java.util.UUID.randomUUID()}.db"
        try {
            val first = Room.databaseBuilder(context, FinanceDatabase::class.java, name).build()
            try {
                val repository = RoomFinanceRepository(first)
                repository.addAccount(Account("cash", "Cash", Money(500, "USD")))
                repository.addTransaction(Transaction("income", TransactionType.INCOME, "cash", Money(100, "USD"), LocalDate.ofEpochDay(20_000)))
                repository.voidTransaction("income", "Not received", LocalDate.ofEpochDay(20_001))
            } finally { first.close() }
            val reopened = Room.databaseBuilder(context, FinanceDatabase::class.java, name).build()
            try {
                val snapshot = RoomFinanceRepository(reopened).snapshot()
                assertEquals(1, snapshot.transactions.size)
                assertEquals("Not received", snapshot.corrections.single().reason)
                assertEquals(500L, snapshot.ledger().balance("cash").minorUnits)
                assertEquals(0L, snapshot.ledger().cashFlow("USD").income.minorUnits)
            } finally { reopened.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test
    fun migrationOneToTwoPreservesAllTablesAndEnablesCorrections() = runBlocking {
        val name = "finance-migration-${java.util.UUID.randomUUID()}.db"
        try {
            createVersionOneDatabase(name).apply {
                execSQL("INSERT INTO accounts VALUES ('cash', 'Cash', 1000, 'USD')")
                execSQL("INSERT INTO categories VALUES ('food', 'Food')")
                execSQL("INSERT INTO transactions (id,kind,sourceAccountId,destinationAccountId,amountMinor,dateEpochDay,categoryId,note,currency) VALUES ('expense','EXPENSE','cash',NULL,100,20000,'food','Original','USD')")
                execSQL("INSERT INTO goals VALUES ('goal','Goal',2000,'USD',21000)")
                execSQL("INSERT INTO goal_reservations VALUES ('reservation','goal','cash',50,20000)")
                execSQL("INSERT INTO reservation_changes (reservationId,amountMinor,dateEpochDay) VALUES ('reservation',50,20000)")
                execSQL("INSERT INTO budgets VALUES ('budget','food',300,'USD',20000,20006)")
                execSQL("INSERT INTO weekly_reviews VALUES ('review',20000,20006,'USD',0,100,'Review')")
                close()
            }
            // Opening through the production factory executes migration and Room's complete schema validation.
            val migrated = FinanceDatabase.open(instrumentation.targetContext, name)
            try {
                val repository = RoomFinanceRepository(migrated)
                val snapshot = repository.snapshot()
                assertEquals(1, snapshot.accounts.size)
                assertEquals(1, snapshot.categories.size)
                assertEquals(1, snapshot.transactions.size)
                assertEquals(1, snapshot.goals.size)
                assertEquals(1, snapshot.reservations.size)
                assertEquals(1, snapshot.budgets.size)
                assertEquals(1, snapshot.weeklyReviews.size)
                assertEquals(1, migrated.financeDao().getReservationChanges().size)
                assertEquals(900L, snapshot.ledger().balance("cash").minorUnits)
                assertEquals(0, snapshot.corrections.size)
                repository.voidTransaction("expense", "Migrated correction")
                assertEquals("Original", repository.snapshot().transactions.single().note)
                assertEquals(1_000L, repository.snapshot().ledger().balance("cash").minorUnits)
            } finally { migrated.close() }
        } finally { instrumentation.targetContext.deleteDatabase(name) }
    }

    private fun createVersionOneDatabase(name: String): android.database.sqlite.SQLiteDatabase {
        val schemaPath = "com.kevinfernandez.appfinanciero.data.FinanceDatabase/1.json"
        val schema = instrumentation.context.assets.open(schemaPath).bufferedReader().use { JSONObject(it.readText()) }
            .getJSONObject("database")
        val database = instrumentation.targetContext.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
        database.setForeignKeyConstraintsEnabled(true)
        val entities = schema.getJSONArray("entities")
        for (index in 0 until entities.length()) {
            val entity = entities.getJSONObject(index)
            val tableName = entity.getString("tableName")
            database.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", tableName))
            val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
            for (position in 0 until indices.length()) {
                database.execSQL(indices.getJSONObject(position).getString("createSql").replace("\${TABLE_NAME}", tableName))
            }
        }
        val setupQueries = schema.getJSONArray("setupQueries")
        for (index in 0 until setupQueries.length()) database.execSQL(setupQueries.getString(index))
        database.version = 1
        return database
    }

    private suspend fun withDatabase(block: suspend (FinanceDatabase) -> Unit) {
        val database = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, FinanceDatabase::class.java).build()
        try { block(database) } finally { database.close() }
    }

    private suspend inline fun <reified T : Throwable> rejects(block: suspend () -> Unit) {
        try { block(); fail("Expected ${T::class.java.simpleName}") }
        catch (error: Throwable) { if (error !is T) throw error }
    }
}
