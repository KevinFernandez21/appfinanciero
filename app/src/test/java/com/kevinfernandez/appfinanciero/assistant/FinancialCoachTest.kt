package com.kevinfernandez.appfinanciero.assistant

import com.kevinfernandez.appfinanciero.domain.Account
import com.kevinfernandez.appfinanciero.domain.CategoryBudget
import com.kevinfernandez.appfinanciero.domain.FinanceLedger
import com.kevinfernandez.appfinanciero.domain.GoalPlanner
import com.kevinfernandez.appfinanciero.domain.GoalReservation
import com.kevinfernandez.appfinanciero.domain.Money
import com.kevinfernandez.appfinanciero.domain.SavingsGoal
import com.kevinfernandez.appfinanciero.domain.Transaction
import com.kevinfernandez.appfinanciero.domain.TransactionType
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FinancialCoachTest {
    private fun usd(units: Long) = Money(units, "USD")
    private val month = YearMonth.of(2026, 10)

    @Test fun summaryUsesFactsNotTransfersOrReservationsAsIncome() {
        val ledger = FinanceLedger(
            listOf(Account("cash", "Cash", usd(10_000)), Account("bank", "Bank", usd(0))),
            listOf(
                Transaction("income", TransactionType.INCOME, "cash", usd(20_000), month.atDay(2)),
                Transaction("transfer", TransactionType.TRANSFER, "cash", usd(5_000), month.atDay(3), destinationAccountId = "bank"),
                Transaction("food", TransactionType.EXPENSE, "bank", usd(1_500), month.atDay(4), categoryId = "food"),
            ),
        )
        val goal = SavingsGoal("goal", "Emergency", usd(10_000), LocalDate.of(2027, 1, 1))
        val planner = GoalPlanner(ledger, listOf(goal), listOf(GoalReservation("goal", "cash", usd(2_000))))

        val summary = FinancialCoach().summarize(ledger, planner, emptyList(), "USD", month)

        assertEquals(usd(20_000), summary.income)
        assertEquals(usd(1_500), summary.expenses)
        assertEquals(usd(18_500), summary.netCashFlow)
        assertEquals(usd(28_500), summary.totalBalance)
        assertEquals(usd(2_000), summary.goals.single().saved)
        assertEquals(usd(8_000), summary.goals.single().remaining)
    }

    @Test fun budgetSpendingIsSpecificToCategoryAndMonth() {
        val ledger = FinanceLedger(listOf(Account("cash", "Cash", usd(10_000))), listOf(
            Transaction("this", TransactionType.EXPENSE, "cash", usd(1_500), month.atDay(4), categoryId = "food"),
            Transaction("other", TransactionType.EXPENSE, "cash", usd(500), month.atDay(5), categoryId = "travel"),
            Transaction("old", TransactionType.EXPENSE, "cash", usd(100), month.minusMonths(1).atDay(5), categoryId = "food"),
        ))
        val planner = GoalPlanner(ledger, emptyList(), emptyList())
        val budget = CategoryBudget("budget", "food", usd(2_000), month.atDay(1), month.atEndOfMonth())

        val summary = FinancialCoach().summarize(ledger, planner, listOf(budget), "USD", month)

        assertEquals(usd(1_500), summary.budgets.single().spent)
        assertFalse(summary.budgets.single().isExceeded)
        assertEquals(usd(2_000), summary.expenses)
    }
}
