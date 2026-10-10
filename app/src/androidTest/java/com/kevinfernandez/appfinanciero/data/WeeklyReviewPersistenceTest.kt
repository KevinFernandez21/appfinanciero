package com.kevinfernandez.appfinanciero.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.kevinfernandez.appfinanciero.domain.*
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class WeeklyReviewPersistenceTest {
    @Test fun savedReviewUsesActiveFactsAndRemainsUnchangedAfterLaterCorrectionsAndReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "weekly-review-${java.util.UUID.randomUUID()}.db"
        val end = LocalDate.of(2026, 1, 3)
        try {
            val db = Room.databaseBuilder(context, FinanceDatabase::class.java, name).build()
            try {
                val repository = RoomFinanceRepository(db)
                repository.addAccount(Account("cash", "Cash", Money(5_000, "USD")))
                repository.addAccount(Account("bank", "Bank", Money(0, "USD")))
                repository.addAccount(Account("yen", "Yen", Money(0, "JPY")))
                repository.addCategory("food", "Food")
                repository.addTransaction(Transaction("income", TransactionType.INCOME, "cash", Money(500, "USD"), end.minusDays(6)))
                repository.addTransaction(Transaction("expense", TransactionType.EXPENSE, "cash", Money(200, "USD"), end, categoryId = "food"))
                repository.addTransaction(Transaction("wrong", TransactionType.EXPENSE, "cash", Money(900, "USD"), end, categoryId = "food"))
                repository.voidTransaction("wrong", "Duplicate", end)
                repository.addTransaction(Transaction("transfer", TransactionType.TRANSFER, "cash", Money(100, "USD"), end, destinationAccountId = "bank"))
                repository.addTransaction(Transaction("yenIncome", TransactionType.INCOME, "yen", Money(700, "JPY"), end))
                repository.addTransaction(Transaction("before", TransactionType.INCOME, "cash", Money(800, "USD"), end.minusDays(7)))
                repository.addTransaction(Transaction("after", TransactionType.EXPENSE, "cash", Money(600, "USD"), end.plusDays(1), categoryId = "food"))
                repository.saveWeeklyReview("review", end.minusDays(6), end, "USD", "My reflection")
                val saved = repository.snapshot().weeklyReviews.single()
                assertEquals(500L, saved.incomeMinor)
                assertEquals(200L, saved.expensesMinor)
                repository.voidTransaction("expense", "Wrong amount", end.plusDays(1))
                assertEquals(saved, repository.snapshot().weeklyReviews.single())
                assertEquals(0L, repository.snapshot().ledger().cashFlow("USD", end.minusDays(6), end).expenses.minorUnits)
            } finally { db.close() }
            val reopened = Room.databaseBuilder(context, FinanceDatabase::class.java, name).build()
            try {
                val saved = RoomFinanceRepository(reopened).snapshot().weeklyReviews.single()
                assertEquals(500L, saved.incomeMinor)
                assertEquals(200L, saved.expensesMinor)
                assertEquals("My reflection", saved.note)
                assertEquals(end.minusDays(6).toEpochDay(), saved.startEpochDay)
                assertEquals(end.toEpochDay(), saved.endEpochDay)
            } finally { reopened.close() }
        } finally { context.deleteDatabase(name) }
    }
}
