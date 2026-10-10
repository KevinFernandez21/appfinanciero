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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {
    @Insert suspend fun insertCorrection(correction: TransactionCorrectionEntity)
    @Query("SELECT * FROM transaction_corrections ORDER BY dateEpochDay DESC, id") suspend fun getCorrections(): List<TransactionCorrectionEntity>
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
        getAccounts(), getTransactions(), getGoals(), getReservations(), getCategories(), getBudgets(), getWeeklyReviews(), getCorrections(),
    )
}

@Database(entities = [AccountEntity::class, TransactionEntity::class, GoalEntity::class, GoalReservationEntity::class,
    CategoryEntity::class, BudgetEntity::class, WeeklyReviewEntity::class, ReservationChangeEntity::class,
    TransactionCorrectionEntity::class], version = 2, exportSchema = true)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun financeDao(): FinanceDao
    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `transaction_corrections` (`id` TEXT NOT NULL, `originalTransactionId` TEXT NOT NULL, `replacementTransactionId` TEXT, `reason` TEXT NOT NULL, `dateEpochDay` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`originalTransactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`replacementTransactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transaction_corrections_originalTransactionId` ON `transaction_corrections` (`originalTransactionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_corrections_replacementTransactionId` ON `transaction_corrections` (`replacementTransactionId`)")
            }
        }
        fun open(context: Context, name: String = "finance.db"): FinanceDatabase = Room.databaseBuilder(
            context.applicationContext, FinanceDatabase::class.java, name,
        ).addMigrations(MIGRATION_1_2).build()
    }
}
