package com.kevinfernandez.appfinanciero.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

data class SavingsGoal(val id: String, val name: String, val target: Money, val deadline: LocalDate) {
    init {
        requireId(id); require(name.isNotBlank()) { "Goal name cannot be blank" }
        require(target.minorUnits > 0) { "Goal target must be positive" }
    }
    fun remaining(saved: Money): Money {
        target.requireSameCurrency(saved)
        require(saved.minorUnits >= 0) { "Saved amount cannot be negative" }
        return target.copy(minorUnits = if (saved.minorUnits >= target.minorUnits) 0 else target.minorUnits - saved.minorUnits)
    }
    /** One contribution in each calendar month, including this month and the deadline month. */
    fun requiredMonthlyContribution(saved: Money, today: LocalDate): Money {
        val remaining = remaining(saved)
        if (!deadline.isAfter(today)) return remaining
        val months = ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(deadline)) + 1
        val units = remaining.minorUnits
        return remaining.copy(minorUnits = units / months + if (units % months == 0L) 0 else 1)
    }
}

/** A virtual earmark, never a transaction or a physical transfer. One total per goal/account pair. */
data class GoalReservation(val goalId: String, val accountId: String, val amount: Money) {
    init {
        requireId(goalId); requireId(accountId)
        require(amount.minorUnits > 0) { "Reservation must be positive" }
    }
}

class GoalPlanner(val ledger: FinanceLedger, goals: List<SavingsGoal>, reservations: List<GoalReservation>) {
    val goals: List<SavingsGoal> = goals.toList()
    val reservations: List<GoalReservation> = reservations.toList()
    private val goalsById = this.goals.associateBy { it.id }
    init {
        require(goalsById.size == this.goals.size) { "Duplicate goal identifier" }
        require(this.reservations.map { it.goalId to it.accountId }.toSet().size == this.reservations.size) {
            "Duplicate reservation for goal and account"
        }
        this.reservations.forEach {
            goal(it.goalId).target.requireSameCurrency(it.amount)
            ledger.account(it.accountId).openingBalance.requireSameCurrency(it.amount)
        }
    }
    /** Validate proposed earmarks before saving; existing earmarks may become underfunded after spending. */
    fun requireFundedReservations() {
        this.reservations.map { it.accountId }.distinct().forEach {
            require(availableBalance(it).minorUnits >= 0) { "Reservations exceed account balance" }
        }
    }
    fun goal(id: String): SavingsGoal = requireNotNull(goalsById[id]) { "Unknown goal: $id" }
    fun saved(goalId: String): Money = reservations.filter { it.goalId == goalId }
        .fold(Money.zero(goal(goalId).target.currency)) { sum, reservation -> sum + reservation.amount }
    fun remaining(goalId: String): Money = goal(goalId).remaining(saved(goalId))
    fun requiredMonthlyContribution(goalId: String, today: LocalDate): Money =
        goal(goalId).requiredMonthlyContribution(saved(goalId), today)
    fun availableBalance(accountId: String): Money = reservations.filter { it.accountId == accountId }
        .fold(ledger.balance(accountId)) { balance, reservation -> balance - reservation.amount }
}
