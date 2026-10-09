package com.kevinfernandez.appfinanciero.assistant

import com.kevinfernandez.appfinanciero.domain.BudgetProgress
import com.kevinfernandez.appfinanciero.domain.CategoryBudget
import com.kevinfernandez.appfinanciero.domain.FinanceLedger
import com.kevinfernandez.appfinanciero.domain.GoalPlanner
import com.kevinfernandez.appfinanciero.domain.Money
import java.time.YearMonth

/** Computed facts suitable for display or for a consented, redacted model prompt. */
data class FinancialSnapshot(
    val month: YearMonth,
    val currency: String,
    val income: Money,
    val expenses: Money,
    val netCashFlow: Money,
    val totalBalance: Money,
    val budgets: List<BudgetProgress>,
    val goals: List<GoalSnapshot>,
)

data class GoalSnapshot(
    val id: String,
    val name: String,
    val target: Money,
    val saved: Money,
    val remaining: Money,
    val requiredMonthlyContribution: Money,
)

/** No model inference is required to calculate or explain these amounts. */
class FinancialCoach {
    fun summarize(
        ledger: FinanceLedger,
        planner: GoalPlanner,
        budgets: List<CategoryBudget>,
        currency: String,
        month: YearMonth,
    ): FinancialSnapshot {
        val flow = ledger.cashFlow(currency, month.atDay(1), month.atEndOfMonth())
        return FinancialSnapshot(
            month = month,
            currency = currency,
            income = flow.income,
            expenses = flow.expenses,
            netCashFlow = flow.net,
            totalBalance = ledger.totalBalance(currency),
            budgets = budgets.filter { it.limit.currency == currency }.map(ledger::budgetProgress),
            goals = planner.goals.filter { it.target.currency == currency }.map { goal ->
                GoalSnapshot(
                    id = goal.id,
                    name = goal.name,
                    target = goal.target,
                    saved = planner.saved(goal.id),
                    remaining = planner.remaining(goal.id),
                    requiredMonthlyContribution = planner.requiredMonthlyContribution(goal.id, month.atDay(1)),
                )
            },
        )
    }
}
