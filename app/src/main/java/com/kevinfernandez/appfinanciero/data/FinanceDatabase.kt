package com.kevinfernandez.appfinanciero.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {
    @Insert suspend fun insertReservationChange(change: ReservationChangeEntity)
    @Update suspend fun updateReservation(reservation: GoalReservationEntity)
    @Query("SELECT * FROM reservation_changes ORDER BY sequence") suspend fun getReservationChanges(): List<ReservationChangeEntity>
    @Insert suspend fun insertAccount(account: AccountEntity)
    @Insert suspend fun insertTransaction(transaction: TransactionEntity)
    @Insert suspend fun insertGoal(goal: GoalEntity)
    @Insert suspend fun insertReservation(reservation: GoalReservationEntity)
    @Insert suspend fun insertCategory(category: CategoryEntity)
    @Insert suspend fun insertBudget(budget: BudgetEntity)
    @Insert suspend fun insertWeeklyReview(review: WeeklyReviewEntity)
    @Query("SELECT * FROM accounts ORDER BY name, id") suspend fun getAccounts(): List<AccountEntity>
    @Query("SELECT * FROM transactions ORDER BY dateEpochDay DESC, id") suspend fun getTransactions(): List<TransactionEntity>
    @Query("SELECT * FROM goals ORDER BY name, id") suspend fun getGoals(): List<GoalEntity>
    @Query("SELECT * FROM goals WHERE id = :id") suspend fun getGoal(id: String): GoalEntity?
    @Query("SELECT * FROM goal_reservations ORDER BY id") suspend fun getReservations(): List<GoalReservationEntity>
    @Query("SELECT * FROM categories ORDER BY name, id") suspend fun getCategories(): List<CategoryEntity>
    @Query("SELECT * FROM budgets ORDER BY startEpochDay DESC, id") suspend fun getBudgets(): List<BudgetEntity>
    @Query("SELECT * FROM weekly_reviews ORDER BY startEpochDay DESC, id") suspend fun getWeeklyReviews(): List<WeeklyReviewEntity>
    @Query("SELECT * FROM transactions ORDER BY dateEpochDay DESC, id") fun observeTransactions(): Flow<List<TransactionEntity>>
    @Transaction
    suspend fun getSnapshot(): FinanceSnapshot = FinanceSnapshot(
        getAccounts(), getTransactions(), getGoals(), getReservations(), getCategories(), getBudgets(), getWeeklyReviews(),
    )
}

@Database(entities = [AccountEntity::class, TransactionEntity::class, GoalEntity::class, GoalReservationEntity::class,
    CategoryEntity::class, BudgetEntity::class, WeeklyReviewEntity::class, ReservationChangeEntity::class], version = 1, exportSchema = true)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun financeDao(): FinanceDao
    companion object {
        fun open(context: Context): FinanceDatabase = Room.databaseBuilder(
            context.applicationContext, FinanceDatabase::class.java, "finance.db",
        ).build()
    }
}
