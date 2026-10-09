package com.kevinfernandez.appfinanciero.data

import androidx.room.withTransaction
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** One consistent database read, not independently combined table emissions. */
data class FinanceSnapshot(
    val accounts: List<AccountEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val goals: List<GoalEntity> = emptyList(),
    val reservations: List<GoalReservationEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val weeklyReviews: List<WeeklyReviewEntity> = emptyList(),
) {
    fun ledger(): FinanceLedger = FinanceLedger(accounts.map { it.toDomain() }, transactions.map { it.toDomain() })
    fun planner(): GoalPlanner {
        val ledger = ledger()
        val domainGoals = goals.map { it.toDomain() }
        val goalById = domainGoals.associateBy { it.id }
        require(reservations.map { it.goalId to it.accountId }.toSet().size == reservations.size) {
            "Duplicate reservation for goal and account"
        }
        val active = reservations.mapNotNull { reservation ->
            require(reservation.amountMinor >= 0) { "Reservation total cannot be negative" }
            val account = ledger.account(reservation.accountId)
            val goal = requireNotNull(goalById[reservation.goalId]) { "Unknown reservation goal" }
            goal.target.requireSameCurrency(account.openingBalance)
            if (reservation.amountMinor == 0L) null else GoalReservation(
                reservation.goalId, reservation.accountId, Money(reservation.amountMinor, account.openingBalance.currency),
            )
        }
        return GoalPlanner(ledger, domainGoals, active)
    }
}

fun AccountEntity.toDomain() = Account(id, name, Money(openingBalanceMinor, currency))
fun TransactionEntity.toDomain() = Transaction(id, TransactionType.valueOf(kind), sourceAccountId,
    Money(amountMinor, currency), LocalDate.ofEpochDay(dateEpochDay), categoryId, destinationAccountId, note.orEmpty())
fun GoalEntity.toDomain() = SavingsGoal(id, name, Money(targetMinor, currency),
    LocalDate.ofEpochDay(deadlineEpochDay))
fun BudgetEntity.toDomain() = CategoryBudget(id, categoryId, Money(limitMinor, currency),
    LocalDate.ofEpochDay(startEpochDay), LocalDate.ofEpochDay(endEpochDay))

/** Validates and writes in the same transaction to prevent stale-snapshot decisions. */
class RoomFinanceRepository(private val database: FinanceDatabase) {
    private val dao = database.financeDao()
    fun observeSnapshot(): Flow<FinanceSnapshot> = database.invalidationTracker.createFlow(
        "accounts", "transactions", "goals", "goal_reservations", "categories", "budgets", "weekly_reviews",
        emitInitialState = true,
    ).map { dao.getSnapshot() }

    suspend fun snapshot(): FinanceSnapshot = dao.getSnapshot()

    suspend fun addAccount(account: Account) = database.withTransaction {
        val current = dao.getSnapshot()
        val ledger = FinanceLedger(current.accounts.map { it.toDomain() } + account, current.transactions.map { it.toDomain() })
        ledger.totalBalance(account.openingBalance.currency)
        dao.insertAccount(AccountEntity(account.id, account.name, account.openingBalance.minorUnits, account.openingBalance.currency))
    }

    suspend fun addCategory(id: String, name: String) {
        require(id.isNotBlank() && name.isNotBlank()) { "Category identifier and name are required" }
        dao.insertCategory(CategoryEntity(id, name))
    }

    suspend fun addTransaction(transaction: Transaction) = database.withTransaction {
        val current = dao.getSnapshot()
        val ledger = FinanceLedger(current.accounts.map { it.toDomain() }, current.transactions.map { it.toDomain() } + transaction)
        // Force checked arithmetic before persistence, not only when a later screen renders.
        ledger.accounts.forEach { ledger.balance(it.id) }
        ledger.totalBalance(transaction.amount.currency)
        val cashFlow = ledger.cashFlow(transaction.amount.currency)
        cashFlow.net
        val existingPlanner = current.planner()
        val candidatePlanner = GoalPlanner(ledger, existingPlanner.goals, existingPlanner.reservations)
        // Spending may underfund existing earmarks, but their available balances must remain representable.
        candidatePlanner.reservations.map { it.accountId }.distinct().forEach {
            candidatePlanner.availableBalance(it)
        }
        dao.insertTransaction(TransactionEntity(transaction.id, transaction.type.name, transaction.accountId,
            transaction.destinationAccountId, transaction.amount.minorUnits, transaction.date.toEpochDay(),
            transaction.categoryId, transaction.note, transaction.amount.currency))
    }

    suspend fun addGoal(goal: SavingsGoal) = database.withTransaction {
        dao.insertGoal(GoalEntity(goal.id, goal.name, goal.target.minorUnits, goal.target.currency, goal.deadline.toEpochDay()))
    }

    suspend fun addReservation(id: String, reservation: GoalReservation, date: LocalDate = LocalDate.now()) = database.withTransaction {
        require(id.isNotBlank()) { "Reservation identifier is required" }
        val current = dao.getSnapshot()
        val planner = current.planner()
        val updated = GoalPlanner(planner.ledger, planner.goals, planner.reservations + reservation)
        require(updated.availableBalance(reservation.accountId).minorUnits >= 0) { "Reservations exceed account balance" }
        updated.saved(reservation.goalId)
        dao.insertReservation(GoalReservationEntity(id, reservation.goalId, reservation.accountId, reservation.amount.minorUnits, date.toEpochDay()))
        dao.insertReservationChange(ReservationChangeEntity(reservationId = id, amountMinor = reservation.amount.minorUnits, dateEpochDay = date.toEpochDay()))
    }

    /** Sets an absolute virtual total; previous totals remain in the append-only change log. */
    suspend fun setReservationAmount(id: String, reservation: GoalReservation, date: LocalDate = LocalDate.now()) = database.withTransaction {
        require(id.isNotBlank()) { "Reservation identifier is required" }
        val snapshot = dao.getSnapshot()
        val existing = snapshot.reservations.firstOrNull { it.id == id }
        if (existing == null) {
            addReservation(id, reservation, date)
        } else {
            require(existing.goalId == reservation.goalId && existing.accountId == reservation.accountId) {
                "Reservation identity cannot change"
            }
            val planner = snapshot.planner()
            val updated = GoalPlanner(planner.ledger, planner.goals, planner.reservations.filterNot {
                it.goalId == reservation.goalId && it.accountId == reservation.accountId
            } + reservation)
            if (reservation.amount.minorUnits > existing.amountMinor) {
                require(updated.availableBalance(reservation.accountId).minorUnits >= 0) { "Reservations exceed account balance" }
            }
            updated.saved(reservation.goalId)
            dao.updateReservation(existing.copy(amountMinor = reservation.amount.minorUnits))
            dao.insertReservationChange(ReservationChangeEntity(reservationId = id, amountMinor = reservation.amount.minorUnits, dateEpochDay = date.toEpochDay()))
        }
    }

    /** Releases a virtual total without deleting its identity or adjustment history. */
    suspend fun releaseReservation(id: String, date: LocalDate = LocalDate.now()) = database.withTransaction {
        require(id.isNotBlank()) { "Reservation identifier is required" }
        val existing = requireNotNull(dao.getReservations().firstOrNull { it.id == id }) { "Unknown reservation" }
        require(existing.amountMinor > 0) { "Reservation is already released" }
        dao.updateReservation(existing.copy(amountMinor = 0))
        dao.insertReservationChange(ReservationChangeEntity(reservationId = id, amountMinor = 0, dateEpochDay = date.toEpochDay()))
    }
    suspend fun addBudget(budget: CategoryBudget) = database.withTransaction {
        dao.insertBudget(BudgetEntity(budget.id, budget.categoryId, budget.limit.minorUnits, budget.limit.currency,
            budget.startDate.toEpochDay(), budget.endDate.toEpochDay()))
    }

    suspend fun saveWeeklyReview(id: String, start: LocalDate, end: LocalDate, currency: String, note: String) = database.withTransaction {
        require(id.isNotBlank()) { "Review identifier is required" }
        val totals = dao.getSnapshot().ledger().cashFlow(currency, start, end)
        dao.insertWeeklyReview(WeeklyReviewEntity(id, start.toEpochDay(), end.toEpochDay(), currency,
            totals.income.minorUnits, totals.expenses.minorUnits, note))
    }
}
