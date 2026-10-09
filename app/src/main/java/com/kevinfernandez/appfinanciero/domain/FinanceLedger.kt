package com.kevinfernandez.appfinanciero.domain

import java.math.BigInteger
import java.time.LocalDate

enum class TransactionType { INCOME, EXPENSE, TRANSFER }

data class Transaction(
    val id: String,
    val type: TransactionType,
    val accountId: String,
    val amount: Money,
    val date: LocalDate,
    val categoryId: String? = null,
    val destinationAccountId: String? = null,
    val note: String = "",
) {
    init {
        requireId(id); requireId(accountId)
        require(amount.minorUnits > 0) { "Transaction amount must be positive" }
        categoryId?.let(::requireId)
        if (type == TransactionType.TRANSFER) {
            require(!destinationAccountId.isNullOrBlank() && destinationAccountId != accountId) {
                "Transfer needs a distinct destination account"
            }
            require(categoryId == null) { "Transfers have no spending category" }
        } else {
            require(destinationAccountId == null) { "Only transfers have a destination" }
            require(type != TransactionType.EXPENSE || categoryId != null) { "Expense needs a category" }
        }
    }
}

data class CashFlow(val income: Money, val expenses: Money) {
    init {
        income.requireSameCurrency(expenses)
        require(income.minorUnits >= 0 && expenses.minorUnits >= 0) { "Cash flow totals cannot be negative" }
    }
    val net: Money get() = income - expenses
}

data class CategoryBudget(
    val id: String, val categoryId: String, val limit: Money,
    val startDate: LocalDate, val endDate: LocalDate,
) {
    init {
        requireId(id); requireId(categoryId)
        require(limit.minorUnits > 0) { "Budget must be positive" }
        require(!endDate.isBefore(startDate)) { "Invalid budget period" }
    }
}

data class BudgetProgress(val budget: CategoryBudget, val spent: Money) {
    init {
        budget.limit.requireSameCurrency(spent)
        require(spent.minorUnits >= 0) { "Budget spending cannot be negative" }
    }
    val remaining: Money get() = budget.limit - spent
    val isExceeded: Boolean get() = spent.minorUnits > budget.limit.minorUnits
}

/** Immutable snapshot of recorded facts, with no exchange-rate assumptions. */
class FinanceLedger(accounts: List<Account>, transactions: List<Transaction>) {
    val accounts: List<Account> = accounts.toList()
    val transactions: List<Transaction> = transactions.toList()
    private val accountById = this.accounts.associateBy { it.id }
    init {
        require(accountById.size == this.accounts.size) { "Duplicate account identifier" }
        require(this.transactions.map { it.id }.toSet().size == this.transactions.size) { "Duplicate transaction identifier" }
        this.transactions.forEach { transaction ->
            account(transaction.accountId).openingBalance.requireSameCurrency(transaction.amount)
            transaction.destinationAccountId?.let { account(it).openingBalance.requireSameCurrency(transaction.amount) }
        }
    }
    fun account(id: String): Account = requireNotNull(accountById[id]) { "Unknown account: $id" }
    /** Snapshot balance is order-independent; only the final value must fit exact minor units. */
    fun balance(accountId: String): Money {
        val opening = account(accountId).openingBalance
        var units = BigInteger.valueOf(opening.minorUnits)
        transactions.forEach {
            val amount = BigInteger.valueOf(it.amount.minorUnits)
            if (it.accountId == accountId) units = when (it.type) {
                TransactionType.INCOME -> units + amount
                TransactionType.EXPENSE, TransactionType.TRANSFER -> units - amount
            }
            if (it.destinationAccountId == accountId) units += amount
        }
        return opening.copy(minorUnits = units.longValueExact())
    }
    fun totalBalance(currency: String): Money {
        val zero = Money.zero(currency)
        val units = accounts.filter { it.openingBalance.currency == currency }
            .fold(BigInteger.ZERO) { sum, account -> sum + BigInteger.valueOf(balance(account.id).minorUnits) }
        return zero.copy(minorUnits = units.longValueExact())
    }
    fun cashFlow(currency: String, startDate: LocalDate = LocalDate.MIN, endDate: LocalDate = LocalDate.MAX): CashFlow {
        require(!endDate.isBefore(startDate)) { "Invalid cash flow period" }
        var income = Money.zero(currency)
        var expenses = Money.zero(currency)
        transactions.filter { it.amount.currency == currency && !it.date.isBefore(startDate) && !it.date.isAfter(endDate) }
            .forEach { when (it.type) {
                TransactionType.INCOME -> income += it.amount
                TransactionType.EXPENSE -> expenses += it.amount
                TransactionType.TRANSFER -> Unit
            } }
        return CashFlow(income, expenses)
    }
    fun budgetProgress(budget: CategoryBudget): BudgetProgress = BudgetProgress(budget,
        transactions.filter {
            it.type == TransactionType.EXPENSE && it.categoryId == budget.categoryId &&
                it.amount.currency == budget.limit.currency && !it.date.isBefore(budget.startDate) && !it.date.isAfter(budget.endDate)
        }.fold(Money.zero(budget.limit.currency)) { sum, transaction -> sum + transaction.amount })
}
