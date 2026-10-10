package com.kevinfernandez.appfinanciero.assistant

import com.kevinfernandez.appfinanciero.data.*
import com.kevinfernandez.appfinanciero.domain.*
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class WeeklyReviewSummaryTest {
    private val today = LocalDate.of(2026, 1, 3)
    private val accounts = listOf(Account("cash", "Cash", Money(50_000, "USD")),
        Account("bank", "Bank", Money(0, "USD")), Account("yen", "Yen", Money(500, "JPY")))
    private fun movement(id: String, type: TransactionType, amount: Long, day: LocalDate) =
        Transaction(id, type, "cash", Money(amount, "USD"), day,
            categoryId = if (type == TransactionType.EXPENSE) "food" else null)

    @Test fun includesExactlySevenDaysAcrossYearBoundary() {
        val ledger = FinanceLedger(accounts, listOf(
            movement("before", TransactionType.INCOME, 900, today.minusDays(7)),
            movement("start", TransactionType.INCOME, 300, today.minusDays(6)),
            movement("end", TransactionType.EXPENSE, 100, today),
            movement("future", TransactionType.EXPENSE, 800, today.plusDays(1))))
        val summary = WeeklyReviewSummary.from(ledger, "USD", today)
        assertEquals(LocalDate.of(2025, 12, 28), summary.start)
        assertEquals(today, summary.end)
        assertEquals(Money(300, "USD"), summary.cashFlow.income)
        assertEquals(Money(100, "USD"), summary.cashFlow.expenses)
        assertEquals(Money(200, "USD"), summary.cashFlow.net)
    }

    @Test fun separatesCurrenciesAndExcludesTransfersAndOpeningBalances() {
        val ledger = FinanceLedger(accounts, listOf(
            movement("expense", TransactionType.EXPENSE, 300, today),
            Transaction("transfer", TransactionType.TRANSFER, "cash", Money(700, "USD"), today, destinationAccountId = "bank"),
            Transaction("yenIncome", TransactionType.INCOME, "yen", Money(100, "JPY"), today)))
        assertEquals(CashFlow(Money(0, "USD"), Money(300, "USD")), WeeklyReviewSummary.from(ledger, "USD", today).cashFlow)
        assertEquals(Money(-300, "USD"), WeeklyReviewSummary.from(ledger, "USD", today).cashFlow.net)
        assertEquals(CashFlow(Money(100, "JPY"), Money(0, "JPY")), WeeklyReviewSummary.from(ledger, "JPY", today).cashFlow)
    }

    @Test fun usesActiveLedgerFactsNotCorrectedOriginalsOrVirtualReservations() {
        val snapshot = FinanceSnapshot(
            accounts = listOf(AccountEntity("cash", "Cash", 50_000, "USD")),
            transactions = listOf(TransactionEntity("wrong", "EXPENSE", "cash", null, 900, today.toEpochDay(), "food", null, "USD"),
                TransactionEntity("replacement", "EXPENSE", "cash", null, 200, today.toEpochDay(), "food", null, "USD")),
            corrections = listOf(TransactionCorrectionEntity("fix", "wrong", "replacement", "Wrong amount", today.toEpochDay())),
            reservations = listOf(GoalReservationEntity("reserved", "goal", "cash", 1_000, today.toEpochDay())))
        assertEquals(CashFlow(Money(0, "USD"), Money(200, "USD")), WeeklyReviewSummary.from(snapshot.ledger(), "USD", today).cashFlow)
    }

    @Test fun emptyLedgerProducesExactZero() {
        assertEquals(CashFlow(Money.zero("USD"), Money.zero("USD")),
            WeeklyReviewSummary.from(FinanceLedger(accounts, emptyList()), "USD", today).cashFlow)
    }
}
