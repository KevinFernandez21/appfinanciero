package com.kevinfernandez.appfinanciero.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(@PrimaryKey val id: String, val name: String, val openingBalanceMinor: Long, val currency: String)

@Entity(tableName = "categories")
data class CategoryEntity(@PrimaryKey val id: String, val name: String)

@Entity(tableName = "transactions", foreignKeys = [
    ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["sourceAccountId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["destinationAccountId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT),
], indices = [Index("sourceAccountId"), Index("destinationAccountId"), Index("categoryId")])
data class TransactionEntity(
    @PrimaryKey val id: String, val kind: String, val sourceAccountId: String,
    val destinationAccountId: String?, val amountMinor: Long, val dateEpochDay: Long,
    val categoryId: String?, val note: String?, val currency: String = "USD",
)

@Entity(tableName = "goals")
data class GoalEntity(@PrimaryKey val id: String, val name: String, val targetMinor: Long, val currency: String, val deadlineEpochDay: Long)

@Entity(tableName = "goal_reservations", foreignKeys = [
    ForeignKey(entity = GoalEntity::class, parentColumns = ["id"], childColumns = ["goalId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.RESTRICT),
], indices = [Index("goalId"), Index("accountId"), Index(value = ["goalId", "accountId"], unique = true)])
data class GoalReservationEntity(@PrimaryKey val id: String, val goalId: String, val accountId: String, val amountMinor: Long, val dateEpochDay: Long)

@Entity(tableName = "budgets", foreignKeys = [
    ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT),
], indices = [Index("categoryId")])
data class BudgetEntity(@PrimaryKey val id: String, val categoryId: String, val limitMinor: Long, val currency: String, val startEpochDay: Long, val endEpochDay: Long)

/** User-confirmed historical summary; never feeds the authoritative ledger. */
@Entity(tableName = "weekly_reviews")
data class WeeklyReviewEntity(@PrimaryKey val id: String, val startEpochDay: Long, val endEpochDay: Long, val currency: String, val incomeMinor: Long, val expensesMinor: Long, val note: String)

/** Append-only audit of explicit changes to virtual reservation totals. */
@Entity(tableName = "reservation_changes", foreignKeys = [
    ForeignKey(entity = GoalReservationEntity::class, parentColumns = ["id"], childColumns = ["reservationId"], onDelete = ForeignKey.RESTRICT),
], indices = [Index("reservationId")])
data class ReservationChangeEntity(@PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val reservationId: String, val amountMinor: Long, val dateEpochDay: Long)
