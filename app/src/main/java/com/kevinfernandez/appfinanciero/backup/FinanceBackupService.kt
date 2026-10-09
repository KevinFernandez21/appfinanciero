package com.kevinfernandez.appfinanciero.backup

import androidx.room.withTransaction
import com.kevinfernandez.appfinanciero.data.*
import com.kevinfernandez.appfinanciero.domain.Money
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

data class BackupPreview(val tableCounts: Map<String, Int>) {
    val recordCount: Int get() = tableCounts.values.sum()
}

/** Plaintext, user-requested backup. Never log or upload its contents. */
class FinanceBackupService(private val database: FinanceDatabase) {
    private val dao = database.financeDao()

    suspend fun exportJson(): String = database.withTransaction {
        BackupCodec.encode(BackupData(dao.getSnapshot(), dao.getReservationChanges()))
    }

    fun previewJson(json: String): BackupPreview = BackupCodec.decode(json).preview()

    /** Only an explicitly confirmed restore into an entirely empty database is supported. */
    suspend fun restoreJson(json: String, confirmed: Boolean) {
        require(confirmed) { "Restore requires explicit confirmation" }
        val backup = BackupCodec.decode(json)
        database.withTransaction {
            val current = BackupData(dao.getSnapshot(), dao.getReservationChanges())
            require(current.preview().recordCount == 0) { "Restore requires an empty database; existing history is preserved" }
            val snapshot = backup.snapshot
            snapshot.accounts.forEach { dao.insertAccount(it) }
            snapshot.categories.forEach { dao.insertCategory(it) }
            snapshot.goals.forEach { dao.insertGoal(it) }
            snapshot.transactions.forEach { dao.insertTransaction(it) }
            snapshot.reservations.forEach { dao.insertReservation(it) }
            backup.changes.forEach { dao.insertReservationChange(it) }
            snapshot.budgets.forEach { dao.insertBudget(it) }
            snapshot.weeklyReviews.forEach { dao.insertWeeklyReview(it) }
        }
    }

    companion object {
        const val MAX_JSON_CHARACTERS = 10 * 1024 * 1024
    }
}

private data class BackupData(val snapshot: FinanceSnapshot, val changes: List<ReservationChangeEntity>) {
    fun preview() = BackupPreview(linkedMapOf(
        "accounts" to snapshot.accounts.size, "categories" to snapshot.categories.size,
        "transactions" to snapshot.transactions.size, "goals" to snapshot.goals.size,
        "goal_reservations" to snapshot.reservations.size, "reservation_changes" to changes.size,
        "budgets" to snapshot.budgets.size, "weekly_reviews" to snapshot.weeklyReviews.size,
    ))

    fun validate() {
        val s = snapshot
        fun <T> unique(rows: List<T>, id: (T) -> String) {
            require(rows.all { id(it).isNotBlank() })
            require(rows.map(id).toSet().size == rows.size)
        }
        unique(s.accounts) { it.id }; unique(s.categories) { it.id }; unique(s.transactions) { it.id }
        unique(s.goals) { it.id }; unique(s.reservations) { it.id }; unique(s.budgets) { it.id }
        unique(s.weeklyReviews) { it.id }
        require(s.categories.all { it.name.isNotBlank() })
        val categories = s.categories.map { it.id }.toSet()
        s.transactions.forEach { require(it.categoryId == null || it.categoryId in categories) }
        val ledger = s.ledger()
        ledger.accounts.forEach { ledger.balance(it.id) }
        ledger.accounts.map { it.openingBalance.currency }.distinct().forEach {
            ledger.totalBalance(it); ledger.cashFlow(it).net
        }
        val planner = s.planner()
        // Historical earmarks may be underfunded after subsequent spending; do not rewrite them.
        planner.goals.forEach { planner.saved(it.id) }
        ledger.accounts.forEach { planner.availableBalance(it.id) }
        s.reservations.forEach {
            require(it.amountMinor >= 0) // Zero retains a released reservation and its audit identity.
            LocalDate.ofEpochDay(it.dateEpochDay)
        }
        s.budgets.forEach { require(it.categoryId in categories); it.toDomain() }
        s.weeklyReviews.forEach {
            require(it.endEpochDay >= it.startEpochDay)
            LocalDate.ofEpochDay(it.startEpochDay); LocalDate.ofEpochDay(it.endEpochDay)
            require(it.incomeMinor >= 0 && it.expensesMinor >= 0)
            Money(it.incomeMinor, it.currency) - Money(it.expensesMinor, it.currency)
        }
        require(changes.map { it.sequence }.toSet().size == changes.size)
        changes.forEach {
            require(it.sequence > 0 && it.amountMinor >= 0)
            require(s.reservations.any { reservation -> reservation.id == it.reservationId })
            LocalDate.ofEpochDay(it.dateEpochDay)
        }
    }
}

private object BackupCodec {
    private const val FORMAT = "appfinanciero-backup"
    private fun row(vararg fields: Pair<String, Any?>) = JSONObject().apply {
        fields.forEach { (key, value) -> put(key, when (value) {
            null -> JSONObject.NULL
            is Long -> value.toString()
            else -> value
        }) }
    }
    private fun <T> array(rows: List<T>, encode: (T) -> JSONObject) = JSONArray().apply {
        rows.forEach { put(encode(it)) }
    }

    fun encode(data: BackupData): String {
        val s = data.snapshot
        val tables = JSONObject()
            .put("accounts", array(s.accounts) { row("id" to it.id, "name" to it.name, "openingBalanceMinor" to it.openingBalanceMinor, "currency" to it.currency) })
            .put("categories", array(s.categories) { row("id" to it.id, "name" to it.name) })
            .put("transactions", array(s.transactions) { row("id" to it.id, "kind" to it.kind, "sourceAccountId" to it.sourceAccountId,
                "destinationAccountId" to it.destinationAccountId, "amountMinor" to it.amountMinor, "dateEpochDay" to it.dateEpochDay,
                "categoryId" to it.categoryId, "note" to it.note, "currency" to it.currency) })
            .put("goals", array(s.goals) { row("id" to it.id, "name" to it.name, "targetMinor" to it.targetMinor, "currency" to it.currency, "deadlineEpochDay" to it.deadlineEpochDay) })
            .put("goal_reservations", array(s.reservations) { row("id" to it.id, "goalId" to it.goalId, "accountId" to it.accountId, "amountMinor" to it.amountMinor, "dateEpochDay" to it.dateEpochDay) })
            .put("reservation_changes", array(data.changes) { row("sequence" to it.sequence, "reservationId" to it.reservationId, "amountMinor" to it.amountMinor, "dateEpochDay" to it.dateEpochDay) })
            .put("budgets", array(s.budgets) { row("id" to it.id, "categoryId" to it.categoryId, "limitMinor" to it.limitMinor, "currency" to it.currency, "startEpochDay" to it.startEpochDay, "endEpochDay" to it.endEpochDay) })
            .put("weekly_reviews", array(s.weeklyReviews) { row("id" to it.id, "startEpochDay" to it.startEpochDay, "endEpochDay" to it.endEpochDay,
                "currency" to it.currency, "incomeMinor" to it.incomeMinor, "expensesMinor" to it.expensesMinor, "note" to it.note) })
        return JSONObject().put("format", FORMAT).put("formatVersion", 1).put("databaseVersion", 1).put("tables", tables).toString().also {
            require(it.length <= FinanceBackupService.MAX_JSON_CHARACTERS) { "Backup exceeds supported size" }
        }
    }

    fun decode(json: String): BackupData {
        require(json.length <= FinanceBackupService.MAX_JSON_CHARACTERS) { "Backup exceeds supported size" }
        try {
            val tokener = JSONTokener(json)
            val root = tokener.nextValue() as? JSONObject ?: error("Object required")
            require(tokener.nextClean() == '\u0000')
            root.keysExactly("format", "formatVersion", "databaseVersion", "tables")
            require(root.string("format") == FORMAT)
            require(root.get("formatVersion") == 1 && root.get("databaseVersion") == 1)
            val tables = root.getJSONObject("tables")
            tables.keysExactly("accounts", "categories", "transactions", "goals", "goal_reservations", "reservation_changes", "budgets", "weekly_reviews")
            val snapshot = FinanceSnapshot(
                accounts = tables.rows("accounts", "id", "name", "openingBalanceMinor", "currency") {
                    AccountEntity(it.string("id"), it.string("name"), it.long("openingBalanceMinor"), it.string("currency")) },
                categories = tables.rows("categories", "id", "name") { CategoryEntity(it.string("id"), it.string("name")) },
                transactions = tables.rows("transactions", "id", "kind", "sourceAccountId", "destinationAccountId", "amountMinor", "dateEpochDay", "categoryId", "note", "currency") {
                    TransactionEntity(it.string("id"), it.string("kind"), it.string("sourceAccountId"), it.nullable("destinationAccountId"),
                        it.long("amountMinor"), it.long("dateEpochDay"), it.nullable("categoryId"), it.nullable("note"), it.string("currency")) },
                goals = tables.rows("goals", "id", "name", "targetMinor", "currency", "deadlineEpochDay") {
                    GoalEntity(it.string("id"), it.string("name"), it.long("targetMinor"), it.string("currency"), it.long("deadlineEpochDay")) },
                reservations = tables.rows("goal_reservations", "id", "goalId", "accountId", "amountMinor", "dateEpochDay") {
                    GoalReservationEntity(it.string("id"), it.string("goalId"), it.string("accountId"), it.long("amountMinor"), it.long("dateEpochDay")) },
                budgets = tables.rows("budgets", "id", "categoryId", "limitMinor", "currency", "startEpochDay", "endEpochDay") {
                    BudgetEntity(it.string("id"), it.string("categoryId"), it.long("limitMinor"), it.string("currency"), it.long("startEpochDay"), it.long("endEpochDay")) },
                weeklyReviews = tables.rows("weekly_reviews", "id", "startEpochDay", "endEpochDay", "currency", "incomeMinor", "expensesMinor", "note") {
                    WeeklyReviewEntity(it.string("id"), it.long("startEpochDay"), it.long("endEpochDay"), it.string("currency"), it.long("incomeMinor"), it.long("expensesMinor"), it.string("note")) },
            )
            val changes = tables.rows("reservation_changes", "sequence", "reservationId", "amountMinor", "dateEpochDay") {
                ReservationChangeEntity(it.long("sequence"), it.string("reservationId"), it.long("amountMinor"), it.long("dateEpochDay")) }
            return BackupData(snapshot, changes).also { it.validate() }
        } catch (_: Exception) {
            // Do not leak financial values or notes from validation/JSON errors to logs or UI.
            throw IllegalArgumentException("Invalid or unsupported finance backup")
        }
    }

    private fun JSONObject.keysExactly(vararg expected: String) {
        require(keys().asSequence().toSet() == expected.toSet())
    }
    private fun JSONObject.string(key: String): String = get(key) as? String ?: error("String required")
    private fun JSONObject.nullable(key: String): String? = if (get(key) == JSONObject.NULL) null else string(key)
    private fun JSONObject.long(key: String): Long = string(key).also {
        require(it.matches(Regex("-?(0|[1-9][0-9]*)")))
    }.toLong()
    private fun <T> JSONObject.rows(table: String, vararg keys: String, read: (JSONObject) -> T): List<T> {
        val array = getJSONArray(table)
        return (0 until array.length()).map { index -> array.getJSONObject(index).also { it.keysExactly(*keys) }.let(read) }
    }
}
