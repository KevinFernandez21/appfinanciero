package com.kevinfernandez.appfinanciero.data

import androidx.room.Room
import com.kevinfernandez.appfinanciero.domain.Account
import com.kevinfernandez.appfinanciero.domain.Money
import com.kevinfernandez.appfinanciero.domain.SavingsGoal
import com.kevinfernandez.appfinanciero.domain.GoalReservation
import com.kevinfernandez.appfinanciero.domain.Transaction
import com.kevinfernandez.appfinanciero.domain.TransactionType
import java.time.LocalDate
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinanceDatabaseTest {
    private lateinit var database: FinanceDatabase

    @Before
    fun createDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun transactionRetainsSourceDestinationAndExactAmount() = runBlocking {
        val dao = database.financeDao()
        dao.insertAccount(AccountEntity("checking", "Checking", 100_00, "USD"))
        dao.insertAccount(AccountEntity("savings", "Savings", 0, "USD"))
        dao.insertTransaction(
            TransactionEntity(
                id = "transfer-1",
                kind = "TRANSFER",
                sourceAccountId = "checking",
                destinationAccountId = "savings",
                amountMinor = 25_01,
                dateEpochDay = 20_000,
                categoryId = null,
                note = null,
            ),
        )

        val saved = dao.getTransactions().single()
        assertEquals("checking", saved.sourceAccountId)
        assertEquals("savings", saved.destinationAccountId)
        assertEquals(25_01, saved.amountMinor)
    }

    @Test
    fun savingsReservationDoesNotCreateLedgerTransaction() = runBlocking {
        val dao = database.financeDao()
        dao.insertAccount(AccountEntity("checking", "Checking", 100_00, "USD"))
        dao.insertGoal(GoalEntity("emergency", "Emergency fund", 200_00, "USD", 21_000))
        dao.insertReservation(
            GoalReservationEntity("reservation-1", "emergency", "checking", 10_00, 20_000),
        )

        assertEquals(0, dao.getTransactions().size)
        assertEquals(10_00, dao.getReservations().single().amountMinor)
        assertNotNull(dao.getGoal("emergency"))
    }
    @Test
    fun categoriesBudgetsAndReviewsRetainTheirPeriods() = runBlocking {
        val dao = database.financeDao()
        dao.insertCategory(CategoryEntity("food", "Food"))
        dao.insertBudget(BudgetEntity("weekly", "food", 5_000, "USD", 20_000, 20_006))
        dao.insertWeeklyReview(WeeklyReviewEntity("review", 20_000, 20_006, "USD", 8_000, 2_000, "Stay consistent"))
        val snapshot = dao.getSnapshot()
        assertEquals("Food", snapshot.categories.single().name)
        assertEquals(20_006L, snapshot.budgets.single().endEpochDay)
        assertEquals("Stay consistent", snapshot.weeklyReviews.single().note)
    }

    @Test
    fun repositoryRejectsCurrencyMismatchWithoutWriting() = runBlocking {
        val repository = RoomFinanceRepository(database)
        repository.addAccount(com.kevinfernandez.appfinanciero.domain.Account("usd", "USD", com.kevinfernandez.appfinanciero.domain.Money(10_000, "USD")))
        repository.addAccount(com.kevinfernandez.appfinanciero.domain.Account("eur", "EUR", com.kevinfernandez.appfinanciero.domain.Money(10_000, "EUR")))
        try {
            repository.addTransaction(com.kevinfernandez.appfinanciero.domain.Transaction(
                "bad", com.kevinfernandez.appfinanciero.domain.TransactionType.TRANSFER,
                "usd", com.kevinfernandez.appfinanciero.domain.Money(100, "USD"),
                java.time.LocalDate.ofEpochDay(20_000), destinationAccountId = "eur",
            ))
            org.junit.Assert.fail("Currency mismatch must be rejected")
        } catch (_: IllegalArgumentException) {
            assertEquals(0, database.financeDao().getTransactions().size)
        }
    }

    @Test
    fun duplicateAccountDoesNotReplaceHistoricalOpeningBalance() = runBlocking {
        val dao = database.financeDao()
        dao.insertAccount(AccountEntity("checking", "Checking", 10_000, "USD"))
        try {
            dao.insertAccount(AccountEntity("checking", "Replacement", 0, "USD"))
            org.junit.Assert.fail("Duplicate identifiers must not replace history")
        } catch (_: android.database.sqlite.SQLiteConstraintException) {
            assertEquals(10_000L, dao.getAccounts().single().openingBalanceMinor)
        }
    }
    @Test
    fun snapshotFlowEmitsPersistedAccounts() = runBlocking {
        val repository = RoomFinanceRepository(database)
        repository.addAccount(com.kevinfernandez.appfinanciero.domain.Account(
            "flow", "Observed", com.kevinfernandez.appfinanciero.domain.Money(123, "USD"),
        ))
        val snapshot = kotlinx.coroutines.withTimeout(5_000) {
            repository.observeSnapshot().first()
        }
        assertEquals("flow", snapshot.accounts.single().id)
        assertEquals(123L, snapshot.ledger().balance("flow").minorUnits)
    }

    @Test
    fun repositoryRejectsOverfundingWithoutChangingLedgerOrReservations() = runBlocking {
        val repository = RoomFinanceRepository(database)
        repository.addAccount(com.kevinfernandez.appfinanciero.domain.Account(
            "cash", "Cash", com.kevinfernandez.appfinanciero.domain.Money(100, "USD"),
        ))
        repository.addGoal(com.kevinfernandez.appfinanciero.domain.SavingsGoal(
            "goal", "Goal", com.kevinfernandez.appfinanciero.domain.Money(1_000, "USD"), java.time.LocalDate.ofEpochDay(21_000),
        ))
        try {
            repository.addReservation("too-large", com.kevinfernandez.appfinanciero.domain.GoalReservation(
                "goal", "cash", com.kevinfernandez.appfinanciero.domain.Money(101, "USD"),
            ))
            org.junit.Assert.fail("Unfunded reservation must be rejected")
        } catch (_: IllegalArgumentException) {
            val snapshot = repository.snapshot()
            assertEquals(0, snapshot.reservations.size)
            assertEquals(0, snapshot.transactions.size)
            assertEquals(100L, snapshot.ledger().balance("cash").minorUnits)
        }
    }
    @Test
    fun reservationTopUpKeepsPreviousTotalsAndDoesNotMoveMoney() = runBlocking {
        val repository = RoomFinanceRepository(database)
        repository.addAccount(com.kevinfernandez.appfinanciero.domain.Account("cash", "Cash", com.kevinfernandez.appfinanciero.domain.Money(1_000, "USD")))
        repository.addGoal(com.kevinfernandez.appfinanciero.domain.SavingsGoal("goal", "Goal", com.kevinfernandez.appfinanciero.domain.Money(2_000, "USD"), java.time.LocalDate.ofEpochDay(21_000)))
        repository.setReservationAmount("reservation", com.kevinfernandez.appfinanciero.domain.GoalReservation("goal", "cash", com.kevinfernandez.appfinanciero.domain.Money(100, "USD")))
        repository.setReservationAmount("reservation", com.kevinfernandez.appfinanciero.domain.GoalReservation("goal", "cash", com.kevinfernandez.appfinanciero.domain.Money(250, "USD")))
        assertEquals(250L, repository.snapshot().reservations.single().amountMinor)
        assertEquals(listOf(100L, 250L), database.financeDao().getReservationChanges().map { it.amountMinor })
        assertEquals(1_000L, repository.snapshot().ledger().balance("cash").minorUnits)
        assertEquals(0, repository.snapshot().transactions.size)
    }
    @Test
    fun diskDatabaseReopensWithoutLosingHistory() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "finance-test-${java.util.UUID.randomUUID()}.db"
        try {
            val first = Room.databaseBuilder(context, FinanceDatabase::class.java, name).build()
            try {
                first.financeDao().insertAccount(AccountEntity("cash", "Cash", 10_000, "USD"))
                first.financeDao().insertTransaction(TransactionEntity("income", "INCOME", "cash", null, 999, 20_000, null, "Retained", "USD"))
            } finally { first.close() }
            val reopened = Room.databaseBuilder(context, FinanceDatabase::class.java, name).build()
            try {
                assertEquals(999L, reopened.financeDao().getTransactions().single().amountMinor)
                assertEquals("Retained", reopened.financeDao().getTransactions().single().note)
                assertEquals(10_000L, reopened.financeDao().getAccounts().single().openingBalanceMinor)
            } finally { reopened.close() }
        } finally { context.deleteDatabase(name) }
    }
    @Test
    fun addingAccountRejectsAggregateCurrencyOverflowAtomically() = runBlocking {
        val repository = RoomFinanceRepository(database)
        repository.addAccount(Account("large", "A", Money(Long.MAX_VALUE, "USD")))
        try {
            repository.addAccount(Account("extra", "B", Money(1, "USD")))
            org.junit.Assert.fail("An unrepresentable currency total must be rejected before persistence")
        } catch (_: ArithmeticException) {
            assertEquals(listOf("large"), repository.snapshot().accounts.map { it.id })
            assertEquals(Long.MAX_VALUE, repository.snapshot().ledger().totalBalance("USD").minorUnits)
        }
    }

    @Test
    fun addingAccountAllowsExactMaximumAndIndependentCurrency() = runBlocking {
        val repository = RoomFinanceRepository(database)
        repository.addAccount(Account("first", "A", Money(Long.MAX_VALUE - 1, "USD")))
        repository.addAccount(Account("second", "B", Money(1, "USD")))
        repository.addAccount(Account("euro", "C", Money(Long.MAX_VALUE, "EUR")))
        assertEquals(Long.MAX_VALUE, repository.snapshot().ledger().totalBalance("USD").minorUnits)
        assertEquals(Long.MAX_VALUE, repository.snapshot().ledger().totalBalance("EUR").minorUnits)
    }

    @Test
    fun reducingReservationDoesNotRequireHistoricallyUnderfundedAccountsToBeFunded() = runBlocking {
        val repository = RoomFinanceRepository(database)
        repository.addAccount(Account("first", "First", Money(1_000, "USD")))
        repository.addAccount(Account("second", "Second", Money(1_000, "USD")))
        repository.addCategory("food", "Food")
        repository.addGoal(SavingsGoal("goal", "Goal", Money(2_000, "USD"), LocalDate.ofEpochDay(21_000)))
        repository.addReservation("one", GoalReservation("goal", "first", Money(900, "USD")))
        repository.addReservation("two", GoalReservation("goal", "second", Money(900, "USD")))
        repository.addTransaction(Transaction("spent-first", TransactionType.EXPENSE, "first", Money(500, "USD"), LocalDate.ofEpochDay(20_000), categoryId = "food"))
        repository.addTransaction(Transaction("spent-second", TransactionType.EXPENSE, "second", Money(500, "USD"), LocalDate.ofEpochDay(20_000), categoryId = "food"))
        repository.setReservationAmount("two", GoalReservation("goal", "second", Money(800, "USD")))
        assertEquals(800L, repository.snapshot().reservations.first { it.id == "two" }.amountMinor)
        assertEquals(-300L, repository.snapshot().planner().availableBalance("second").minorUnits)
        assertEquals(-400L, repository.snapshot().planner().availableBalance("first").minorUnits)
        assertEquals(listOf(900L, 900L, 800L), database.financeDao().getReservationChanges().map { it.amountMinor })
    }

    @Test
    fun increasingReservationOnlyRequiresAffectedAccountFunding() = runBlocking {
        val repository = RoomFinanceRepository(database)
        repository.addAccount(Account("first", "First", Money(1_000, "USD")))
        repository.addAccount(Account("second", "Second", Money(1_000, "USD")))
        repository.addCategory("food", "Food")
        repository.addGoal(SavingsGoal("goal", "Goal", Money(2_000, "USD"), LocalDate.ofEpochDay(21_000)))
        repository.addReservation("one", GoalReservation("goal", "first", Money(900, "USD")))
        repository.addReservation("two", GoalReservation("goal", "second", Money(100, "USD")))
        repository.addTransaction(Transaction("spent-first", TransactionType.EXPENSE, "first", Money(500, "USD"), LocalDate.ofEpochDay(20_000), categoryId = "food"))
        repository.setReservationAmount("two", GoalReservation("goal", "second", Money(200, "USD")))
        assertEquals(800L, repository.snapshot().planner().availableBalance("second").minorUnits)
        try {
            repository.setReservationAmount("two", GoalReservation("goal", "second", Money(1_001, "USD")))
            org.junit.Assert.fail("New funding must fit within the affected account")
        } catch (_: IllegalArgumentException) {
            assertEquals(200L, repository.snapshot().reservations.first { it.id == "two" }.amountMinor)
            assertEquals(3, database.financeDao().getReservationChanges().size)
        }
    }
    @Test
    fun releasingReservationKeepsZeroAuditAndAllowsLaterTopUpAfterReopening() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "finance-release-test-${java.util.UUID.randomUUID()}.db"
        try {
            val first = Room.databaseBuilder(context, FinanceDatabase::class.java, name).build()
            try {
                val repository = RoomFinanceRepository(first)
                repository.addAccount(Account("cash", "Cash", Money(1_000, "USD")))
                repository.addGoal(SavingsGoal("goal", "Goal", Money(2_000, "USD"), LocalDate.ofEpochDay(21_000)))
                repository.addReservation("reservation", GoalReservation("goal", "cash", Money(800, "USD")), LocalDate.ofEpochDay(20_000))
                repository.releaseReservation("reservation", LocalDate.ofEpochDay(20_001))
                assertEquals(0L, repository.snapshot().reservations.single().amountMinor)
                assertEquals(0L, repository.snapshot().planner().saved("goal").minorUnits)
                assertEquals(1_000L, repository.snapshot().planner().availableBalance("cash").minorUnits)
            } finally { first.close() }
            val reopened = Room.databaseBuilder(context, FinanceDatabase::class.java, name).build()
            try {
                val repository = RoomFinanceRepository(reopened)
                assertEquals(0L, repository.snapshot().planner().saved("goal").minorUnits)
                assertEquals(listOf(800L, 0L), reopened.financeDao().getReservationChanges().map { it.amountMinor })
                repository.setReservationAmount("reservation", GoalReservation("goal", "cash", Money(100, "USD")), LocalDate.ofEpochDay(20_002))
                assertEquals(100L, repository.snapshot().planner().saved("goal").minorUnits)
                assertEquals(listOf(800L, 0L, 100L), reopened.financeDao().getReservationChanges().map { it.amountMinor })
                assertEquals(20_000L, repository.snapshot().reservations.single().dateEpochDay)
                assertEquals(0, repository.snapshot().transactions.size)
            } finally { reopened.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test
    fun releaseRejectsUnknownReservationWithoutAuditAndCanReleaseAnUnderfundedOne() = runBlocking {
        val repository = RoomFinanceRepository(database)
        try {
            repository.releaseReservation("unknown")
            org.junit.Assert.fail("Unknown reservation must not create a release audit")
        } catch (_: IllegalArgumentException) {
            assertEquals(0, database.financeDao().getReservationChanges().size)
        }
        repository.addAccount(Account("cash", "Cash", Money(1_000, "USD")))
        repository.addCategory("food", "Food")
        repository.addGoal(SavingsGoal("goal", "Goal", Money(2_000, "USD"), LocalDate.ofEpochDay(21_000)))
        repository.addReservation("reservation", GoalReservation("goal", "cash", Money(800, "USD")))
        repository.addTransaction(Transaction("spent", TransactionType.EXPENSE, "cash", Money(500, "USD"), LocalDate.ofEpochDay(20_000), categoryId = "food"))
        repository.releaseReservation("reservation")
        assertEquals(500L, repository.snapshot().planner().availableBalance("cash").minorUnits)
        assertEquals(listOf(800L, 0L), database.financeDao().getReservationChanges().map { it.amountMinor })
    }
    @Test
    fun transferRejectsUnrepresentableAvailableBalanceBeforePersistence() = runBlocking {
        val repository = RoomFinanceRepository(database)
        repository.addAccount(Account("a", "A", Money(1, "USD")))
        repository.addAccount(Account("b", "B", Money(Long.MIN_VALUE, "USD")))
        repository.addGoal(SavingsGoal("goal", "Goal", Money(1, "USD"), LocalDate.ofEpochDay(21_000)))
        repository.addReservation("reservation", GoalReservation("goal", "a", Money(1, "USD")))
        repository.addTransaction(Transaction("first", TransactionType.TRANSFER, "a", Money(Long.MAX_VALUE, "USD"), LocalDate.ofEpochDay(20_000), destinationAccountId = "b"))
        try {
            repository.addTransaction(Transaction("overflow", TransactionType.TRANSFER, "a", Money(2, "USD"), LocalDate.ofEpochDay(20_001), destinationAccountId = "b"))
            org.junit.Assert.fail("Available balance overflow must reject the candidate ledger before persistence")
        } catch (_: ArithmeticException) {
            val snapshot = repository.snapshot()
            assertEquals(listOf("first"), snapshot.transactions.map { it.id })
            assertEquals(Long.MIN_VALUE + 2, snapshot.ledger().balance("a").minorUnits)
            assertEquals(-1L, snapshot.ledger().balance("b").minorUnits)
            assertEquals(Long.MIN_VALUE + 1, snapshot.planner().availableBalance("a").minorUnits)
            assertEquals(1, database.financeDao().getReservationChanges().size)
        }
    }

    @Test
    fun transferAllowsHistoricallyUnderfundedAvailableBalanceAtExactMinimum() = runBlocking {
        val repository = RoomFinanceRepository(database)
        repository.addAccount(Account("a", "A", Money(1, "USD")))
        repository.addAccount(Account("b", "B", Money(Long.MIN_VALUE, "USD")))
        repository.addGoal(SavingsGoal("goal", "Goal", Money(1, "USD"), LocalDate.ofEpochDay(21_000)))
        repository.addReservation("reservation", GoalReservation("goal", "a", Money(1, "USD")))
        repository.addTransaction(Transaction("first", TransactionType.TRANSFER, "a", Money(Long.MAX_VALUE, "USD"), LocalDate.ofEpochDay(20_000), destinationAccountId = "b"))
        repository.addTransaction(Transaction("minimum", TransactionType.TRANSFER, "a", Money(1, "USD"), LocalDate.ofEpochDay(20_001), destinationAccountId = "b"))
        val snapshot = repository.snapshot()
        assertEquals(2, snapshot.transactions.size)
        assertEquals(Long.MIN_VALUE, snapshot.planner().availableBalance("a").minorUnits)
        assertEquals(0L, snapshot.ledger().balance("b").minorUnits)
        assertEquals(0L, snapshot.ledger().cashFlow("USD").net.minorUnits)
    }
}
