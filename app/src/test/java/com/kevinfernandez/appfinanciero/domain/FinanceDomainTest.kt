package com.kevinfernandez.appfinanciero.domain

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class FinanceDomainTest {
    private fun usd(units: Long) = Money(units, "USD")
    private val today = LocalDate.of(2026, 10, 9)
    private val cash = Account("cash", "Cash", usd(10000))
    private val bank = Account("bank", "Bank", usd(20000))
    private fun transaction(id: String, type: TransactionType, amount: Long,
        account: String = "cash", category: String? = null, destination: String? = null,
        date: LocalDate = today) = Transaction(id, type, account, usd(amount), date, category, destination)
    private inline fun <reified T : Throwable> rejects(block: () -> Unit) {
        try { block(); fail("Expected ${T::class.java.simpleName}") }
        catch (error: Throwable) { if (error !is T) throw error }
    }

    @Test fun decimalParsingIsExactAndUsesCurrencyScale() {
        assertEquals(usd(1234), Money.parse("12.34", "USD"))
        assertEquals(Money(12, "JPY"), Money.parse("12", "JPY"))
        rejects<ArithmeticException> { Money.parse("1.001", "USD") }
        rejects<IllegalArgumentException> { Money(1, "usd") }
        rejects<IllegalArgumentException> { Money(1, "ZZZ") }
    }
    @Test fun arithmeticRejectsOverflowAndCurrencyMismatch() {
        rejects<ArithmeticException> { usd(Long.MAX_VALUE) + usd(1) }
        rejects<ArithmeticException> { usd(Long.MIN_VALUE) - usd(1) }
        rejects<IllegalArgumentException> { usd(1) + Money(1, "EUR") }
        assertEquals(usd(-50), usd(50) - usd(100))
    }
    @Test fun transactionsRequirePositiveAmountsAndCorrectFields() {
        rejects<IllegalArgumentException> { transaction("x", TransactionType.INCOME, 0) }
        rejects<IllegalArgumentException> { transaction("x", TransactionType.EXPENSE, -1, category = "food") }
        rejects<IllegalArgumentException> { transaction("x", TransactionType.EXPENSE, 1) }
        rejects<IllegalArgumentException> { transaction("x", TransactionType.TRANSFER, 1, destination = "cash") }
        rejects<IllegalArgumentException> { transaction("x", TransactionType.TRANSFER, 1, category = "food", destination = "bank") }
        rejects<IllegalArgumentException> { transaction("x", TransactionType.INCOME, 1, destination = "bank") }
    }
    @Test fun transfersChangeAccountsButNotIncomeExpensesOrNetFlow() {
        val ledger = FinanceLedger(listOf(cash, bank), listOf(
            transaction("income", TransactionType.INCOME, 5000),
            transaction("expense", TransactionType.EXPENSE, 2000, category = "food"),
            transaction("transfer", TransactionType.TRANSFER, 3000, destination = "bank")))
        assertEquals(usd(10000), ledger.balance("cash"))
        assertEquals(usd(23000), ledger.balance("bank"))
        assertEquals(usd(33000), ledger.totalBalance("USD"))
        assertEquals(CashFlow(usd(5000), usd(2000)), ledger.cashFlow("USD"))
        assertEquals(usd(3000), ledger.cashFlow("USD").net)
    }
    @Test fun ledgerRejectsDuplicateIdsUnknownAccountsAndCrossCurrencyTransfers() {
        rejects<IllegalArgumentException> { FinanceLedger(listOf(cash, cash), emptyList()) }
        val income = transaction("same", TransactionType.INCOME, 1)
        rejects<IllegalArgumentException> { FinanceLedger(listOf(cash), listOf(income, income)) }
        rejects<IllegalArgumentException> { FinanceLedger(listOf(cash), listOf(transaction("x", TransactionType.INCOME, 1, account = "missing"))) }
        rejects<IllegalArgumentException> { FinanceLedger(listOf(cash), listOf(transaction("x", TransactionType.TRANSFER, 1, destination = "missing"))) }
        rejects<IllegalArgumentException> { FinanceLedger(listOf(cash, Account("eur", "Euro", Money(1, "EUR"))), listOf(transaction("x", TransactionType.TRANSFER, 1, destination = "eur"))) }
    }
    @Test fun cashFlowDateRangeIsInclusiveAndCurrencySeparated() {
        val ledger = FinanceLedger(listOf(cash), listOf(
            transaction("old", TransactionType.INCOME, 100, date = today.minusDays(1)),
            transaction("now", TransactionType.INCOME, 200)))
        assertEquals(usd(200), ledger.cashFlow("USD", today, today).income)
        assertEquals(Money(0, "EUR"), ledger.cashFlow("EUR").income)
        rejects<IllegalArgumentException> { ledger.cashFlow("USD", today, today.minusDays(1)) }
    }
    @Test fun budgetTracksOnlyCategoryExpensesInsidePeriodAndExposesOverspend() {
        val ledger = FinanceLedger(listOf(cash, bank), listOf(
            transaction("food", TransactionType.EXPENSE, 1200, category = "food"),
            transaction("rent", TransactionType.EXPENSE, 800, category = "rent"),
            transaction("old", TransactionType.EXPENSE, 400, category = "food", date = today.minusMonths(1)),
            transaction("move", TransactionType.TRANSFER, 200, destination = "bank")))
        val progress = ledger.budgetProgress(CategoryBudget("b", "food", usd(1000), today.withDayOfMonth(1), today.withDayOfMonth(31)))
        assertEquals(usd(1200), progress.spent)
        assertEquals(usd(-200), progress.remaining)
        assertTrue(progress.isExceeded)
    }
    @Test fun goalReservationsDoNotMoveMoneyAndCannotExceedAvailableBalance() {
        val ledger = FinanceLedger(listOf(cash), emptyList())
        val goal = SavingsGoal("g", "Trip", usd(15000), today.plusMonths(2))
        val planner = GoalPlanner(ledger, listOf(goal), listOf(GoalReservation("g", "cash", usd(4000))))
        assertEquals(usd(10000), ledger.balance("cash"))
        assertEquals(usd(6000), planner.availableBalance("cash"))
        assertEquals(usd(4000), planner.saved("g"))
        assertEquals(usd(11000), planner.remaining("g"))
        rejects<IllegalArgumentException> { GoalPlanner(ledger, listOf(goal), listOf(GoalReservation("g", "cash", usd(10001)))).requireFundedReservations() }
        rejects<IllegalArgumentException> { GoalPlanner(ledger, listOf(goal), listOf(GoalReservation("missing", "cash", usd(1)))) }
    }
    @Test fun reservationsAcrossGoalsAreCombinedAndDuplicatePairsRejected() {
        val ledger = FinanceLedger(listOf(cash), emptyList())
        val g1 = SavingsGoal("g1", "Trip", usd(10000), today)
        val g2 = SavingsGoal("g2", "Car", usd(10000), today)
        rejects<IllegalArgumentException> { GoalPlanner(ledger, listOf(g1, g2), listOf(GoalReservation("g1", "cash", usd(6000)), GoalReservation("g2", "cash", usd(5000)))).requireFundedReservations() }
        rejects<IllegalArgumentException> { GoalPlanner(ledger, listOf(g1), listOf(GoalReservation("g1", "cash", usd(1)), GoalReservation("g1", "cash", usd(1)))) }
    }
    @Test fun monthlyContributionRoundsUpAndOverdueGoalsRequireAllRemaining() {
        val goal = SavingsGoal("g", "Trip", usd(100), today.plusMonths(2))
        assertEquals(usd(34), goal.requiredMonthlyContribution(usd(0), today))
        assertEquals(usd(100), goal.requiredMonthlyContribution(usd(0), today.plusMonths(3)))
        assertEquals(usd(0), goal.remaining(usd(120)))
        assertEquals(usd(0), goal.requiredMonthlyContribution(usd(120), today))
        assertEquals(usd(Long.MAX_VALUE / 3 + 1), goal.copy(target = usd(Long.MAX_VALUE)).requiredMonthlyContribution(usd(0), today))
    }
    @Test fun goalsBudgetsAndReservationsRejectInvalidValues() {
        rejects<IllegalArgumentException> { SavingsGoal("g", "", usd(1), today) }
        rejects<IllegalArgumentException> { SavingsGoal("g", "Trip", usd(0), today) }
        rejects<IllegalArgumentException> { GoalReservation("g", "cash", usd(0)) }
        rejects<IllegalArgumentException> { CategoryBudget("b", "food", usd(1), today, today.minusDays(1)) }
        rejects<IllegalArgumentException> { SavingsGoal("g", "Trip", usd(1), today).remaining(usd(-1)) }
    }
    @Test fun summariesRejectNegativeTotalsAndCurrencyMismatch() {
        rejects<IllegalArgumentException> { CashFlow(usd(-1), usd(0)) }
        rejects<IllegalArgumentException> { CashFlow(usd(0), usd(-1)) }
        rejects<IllegalArgumentException> { CashFlow(usd(0), Money(0, "EUR")) }
        val budget = CategoryBudget("b", "food", usd(100), today, today)
        rejects<IllegalArgumentException> { BudgetProgress(budget, usd(-1)) }
    }
    @Test fun calculationsFailExplicitlyOnBalanceOrTotalOverflow() {
        val rich = Account("rich", "Rich", usd(Long.MAX_VALUE))
        val income = transaction("income", TransactionType.INCOME, 1, account = "rich")
        rejects<ArithmeticException> { FinanceLedger(listOf(rich), listOf(income)).balance("rich") }
        rejects<ArithmeticException> { FinanceLedger(listOf(rich, cash), emptyList()).totalBalance("USD") }
    }
    @Test fun reservationsRejectUnknownAccountsAndMismatchedGoalCurrencies() {
        val ledger = FinanceLedger(listOf(cash), emptyList())
        val goal = SavingsGoal("g", "Trip", usd(100), today)
        rejects<IllegalArgumentException> { GoalPlanner(ledger, listOf(goal), listOf(GoalReservation("g", "missing", usd(1)))) }
        rejects<IllegalArgumentException> { GoalPlanner(ledger, listOf(goal), listOf(GoalReservation("g", "cash", Money(1, "EUR")))) }
        rejects<IllegalArgumentException> { GoalPlanner(ledger, listOf(goal, goal), emptyList()) }
    }
    @Test fun snapshotsDoNotObserveLaterMutationOfInputCollections() {
        val accounts = mutableListOf(cash)
        val entries = mutableListOf(transaction("income", TransactionType.INCOME, 100))
        val ledger = FinanceLedger(accounts, entries)
        accounts.clear(); entries.clear()
        assertEquals(usd(10100), ledger.balance("cash"))
    }
    @Test fun debtAccountsRemainValidButCannotReserveMoney() {
        val debt = Account("debt", "Credit", usd(-100))
        val ledger = FinanceLedger(listOf(debt), emptyList())
        assertEquals(usd(-100), ledger.balance("debt"))
        val goal = SavingsGoal("g", "Trip", usd(100), today)
        rejects<IllegalArgumentException> { GoalPlanner(ledger, listOf(goal), listOf(GoalReservation("g", "debt", usd(1)))).requireFundedReservations() }
    }
    @Test fun spendingDoesNotMakeExistingOverAllocatedReservationsUnreadable() {
        val goal = SavingsGoal("g", "Trip", usd(10000), today)
        val reservations = listOf(GoalReservation("g", "cash", usd(9000)))
        GoalPlanner(FinanceLedger(listOf(cash), emptyList()), listOf(goal), reservations).requireFundedReservations()
        val afterExpense = FinanceLedger(listOf(cash), listOf(transaction("food", TransactionType.EXPENSE, 2000, category = "food")))
        val planner = GoalPlanner(afterExpense, listOf(goal), reservations)
        assertEquals(usd(8000), planner.ledger.balance("cash"))
        assertEquals(usd(9000), planner.saved("g"))
        assertEquals(usd(-1000), planner.availableBalance("cash"))
        rejects<IllegalArgumentException> { planner.requireFundedReservations() }
    }
    @Test fun balancesAreIndependentOfTransactionOrderAtLongBounds() {
        val incoming = transaction("income", TransactionType.INCOME, 1)
        val outgoing = transaction("expense", TransactionType.EXPENSE, 1, category = "food")
        for (opening in listOf(Long.MAX_VALUE, Long.MIN_VALUE)) {
            val account = cash.copy(openingBalance = usd(opening))
            for (entries in listOf(listOf(incoming, outgoing), listOf(outgoing, incoming))) {
                assertEquals(usd(opening), FinanceLedger(listOf(account), entries).balance("cash"))
            }
        }
    }
    @Test fun transfersAtLongBoundsAreIndependentOfEntryOrder() {
        val fromBank = transaction("fromBank", TransactionType.TRANSFER, 1, account = "bank", destination = "cash")
        val toBank = transaction("toBank", TransactionType.TRANSFER, 1, destination = "bank")
        val accounts = listOf(cash.copy(openingBalance = usd(Long.MAX_VALUE)), bank.copy(openingBalance = usd(0)))
        for (entries in listOf(listOf(fromBank, toBank), listOf(toBank, fromBank))) {
            val ledger = FinanceLedger(accounts, entries)
            assertEquals(usd(Long.MAX_VALUE), ledger.balance("cash"))
            assertEquals(usd(0), ledger.balance("bank"))
            assertEquals(CashFlow(usd(0), usd(0)), ledger.cashFlow("USD"))
        }
    }
    @Test fun totalBalancesAreIndependentOfAccountOrderAtLongBounds() {
        for (extreme in listOf(Long.MAX_VALUE, Long.MIN_VALUE)) {
            val rich = Account("extreme", "Extreme", usd(extreme))
            val positive = Account("positive", "Positive", usd(1))
            val negative = Account("negative", "Negative", usd(-1))
            for (accounts in listOf(listOf(rich, positive, negative), listOf(rich, negative, positive), listOf(positive, negative, rich))) {
                assertEquals(usd(extreme), FinanceLedger(accounts, emptyList()).totalBalance("USD"))
            }
        }
    }
    @Test fun cashFlowRejectsOutOfRangeGrossTotalsEvenWhenNetWouldFit() {
        val income = transaction("income", TransactionType.INCOME, Long.MAX_VALUE)
        val extra = transaction("extra", TransactionType.INCOME, 1)
        val expense = transaction("expense", TransactionType.EXPENSE, 1, category = "food")
        for (entries in listOf(listOf(income, extra, expense), listOf(expense, extra, income))) {
            rejects<ArithmeticException> { FinanceLedger(listOf(cash), entries).cashFlow("USD") }
        }
    }
}
