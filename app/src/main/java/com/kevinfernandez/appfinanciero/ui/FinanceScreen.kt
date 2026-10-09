package com.kevinfernandez.appfinanciero.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevinfernandez.appfinanciero.data.*
import com.kevinfernandez.appfinanciero.domain.*
import com.kevinfernandez.appfinanciero.voice.OnDeviceSpeechInput
import com.kevinfernandez.appfinanciero.assistant.FinancialCoach
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@Composable
fun FinanceScreen(viewModel: FinanceViewModel) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val restorePreview by viewModel.restorePreview.collectAsStateWithLifecycle()
    val backupNotice by viewModel.backupNotice.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.applicationContext.contentResolver
    val exportDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.exportBackup(resolver, it) }
    }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.previewBackup(resolver, it) }
    }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var form by rememberSaveable { mutableStateOf<String?>(null) }
    var releaseReservationId by rememberSaveable { mutableStateOf<String?>(null) }
    val pages = listOf("Dashboard", "Accounts", "Activity", "Plans", "Settings", "AI")
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("AppAhorro", style = MaterialTheme.typography.headlineMedium)
            Text("Private · Offline · Your ledger is the source of truth", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                pages.forEachIndexed { index, title -> TextButton(onClick = { page = index }) { Text(title) } }
            }
            error?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = viewModel::clearError) { Text("Dismiss") }
            }
            if (snapshot == null) {
                CircularProgressIndicator()
            } else {
                val current = snapshot!!
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (page) {
                        0 -> Dashboard(current)
                        1 -> {
                            Button(onClick = { form = "Account" }, enabled = !busy) { Text("Add account") }
                            if (current.accounts.isEmpty()) Text("Start with a cash or bank account and its opening balance.")
                            val ledger = current.ledger()
                            current.accounts.forEach { account ->
                                RecordCard(account.name, "Balance: ${ledger.balance(account.id).display()}")
                            }
                            Button(onClick = { form = "Category" }, enabled = !busy) { Text("Add category") }
                            current.categories.forEach { RecordCard(it.name, "Spending category") }
                        }
                        2 -> {
                            Button(onClick = { form = "Transaction" }, enabled = !busy && current.accounts.isNotEmpty()) { Text("Record movement") }
                            if (current.accounts.isEmpty()) Text("Add an account before recording a movement.")
                            if (current.transactions.isEmpty()) Text("No recorded movements yet.")
                            current.transactions.forEach { transaction ->
                                val account = current.accounts.firstOrNull { it.id == transaction.sourceAccountId }?.name.orEmpty()
                                val destination = current.accounts.firstOrNull { it.id == transaction.destinationAccountId }?.name
                                RecordCard("${transaction.kind.lowercase().replaceFirstChar { it.uppercase() }} · ${Money(transaction.amountMinor, transaction.currency).display()}",
                                    "$account${destination?.let { " → $it" }.orEmpty()} · ${LocalDate.ofEpochDay(transaction.dateEpochDay)}\n${transaction.note.orEmpty()}")
                            }
                        }
                        3 -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { form = "Goal" }, enabled = !busy) { Text("Add goal") }
                                Button(onClick = { form = "Budget" }, enabled = !busy && current.categories.isNotEmpty()) { Text("Add budget") }
                            }
                            if (current.categories.isEmpty()) Text("Add a spending category before setting a budget.")
                            val planner = current.planner()
                            current.goals.forEach { goal ->
                                RecordCard(goal.name, "Reserved: ${planner.saved(goal.id).display()} / ${Money(goal.targetMinor, goal.currency).display()}\nMonthly plan: ${planner.requiredMonthlyContribution(goal.id, LocalDate.now()).display()}\nDeadline: ${goal.deadlineEpochDay?.let(LocalDate::ofEpochDay)}")
                            }
                            Button(onClick = { form = "Reservation" }, enabled = !busy && current.goals.isNotEmpty() && current.accounts.isNotEmpty()) { Text("Reserve for a goal") }
                            Text("Reservations earmark money; they do not transfer it. Enter the new total to update an existing reservation.")
                            current.reservations.filter { it.amountMinor > 0 }.forEach { reservation ->
                                val goal = current.goals.first { it.id == reservation.goalId }
                                val account = current.accounts.first { it.id == reservation.accountId }
                                RecordCard("${goal.name} · ${account.name}", "Reserved: ${Money(reservation.amountMinor, account.currency).display()}")
                                OutlinedButton(onClick = { releaseReservationId = reservation.id }, enabled = !busy) { Text("Release reservation") }
                            }
                            current.budgets.forEach { budget ->
                                val progress = current.ledger().budgetProgress(budget.toDomain())
                                RecordCard(current.categories.firstOrNull { it.id == budget.categoryId }?.name ?: "Budget", "Spent: ${progress.spent.display()} / ${progress.budget.limit.display()}\n${if (progress.isExceeded) "Over budget" else "Remaining: ${progress.remaining.display()}"}")
                            }
                        }
                        4 -> {
                            Text("Backup & restore", style = MaterialTheme.typography.titleLarge)
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("WARNING: BACKUP FILES ARE NOT ENCRYPTED", style = MaterialTheme.typography.titleMedium)
                                    Text("The JSON contains your accounts, amounts, notes and financial history. Anyone with this file can read it. Choose a secure location and do not share it publicly.")
                                }
                            }
                            Text("Restore is supported only into an entirely empty database. Existing history is never replaced or merged.")
                            Button(onClick = { exportDocument.launch("appahorro-backup-${LocalDate.now()}.json") }, enabled = !busy && restorePreview == null) { Text("Export JSON backup") }
                            OutlinedButton(onClick = { openDocument.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, enabled = !busy && restorePreview == null) { Text("Preview restore from JSON") }
                            if (busy) CircularProgressIndicator()
                            backupNotice?.let { Text(it) }
                        }
                        5 -> LocalAiScreen(viewModel)
                    }
                }
            }
        }
    }
    snapshot?.let { current ->
        current.reservations.firstOrNull { it.id == releaseReservationId && it.amountMinor > 0 }?.let { reservation ->
            val goal = current.goals.first { it.id == reservation.goalId }
            val account = current.accounts.first { it.id == reservation.accountId }
            ReleaseReservationDialog(goal.name, account.name, Money(reservation.amountMinor, account.currency).display(), busy, error,
                onDismiss = { if (!busy) { releaseReservationId = null; viewModel.clearError() } },
                onConfirm = { viewModel.save({ releaseReservation(reservation.id) }, onSuccess = { releaseReservationId = null }) })
        }
    }
    restorePreview?.let { preview ->
        AlertDialog(onDismissRequest = viewModel::cancelRestore,
            title = { Text("Confirm restore") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This backup is NOT encrypted.", color = MaterialTheme.colorScheme.error)
                    Text("${preview.recordCount} records were validated. Import only if you trust this file. Your database must be empty.")
                    preview.tableCounts.forEach { (table, count) -> Text("${table.replace('_', ' ')}: $count") }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::confirmRestore, enabled = !busy) { Text(if (busy) "Restoring…" else "Confirm import") } },
            dismissButton = { TextButton(onClick = viewModel::cancelRestore, enabled = !busy) { Text("Cancel") } })
    }
    form?.let { kind -> snapshot?.let { current ->
        EntryDialog(kind, current, busy, error, onDismiss = { if (!busy) { form = null; viewModel.clearError() } }) { values ->
            viewModel.save({
                val id = UUID.randomUUID().toString()
                fun value(key: String) = values[key].orEmpty().trim()
                fun amount() = FinanceInput.money(value("Amount"), value("Currency"))
                when (kind) {
                    "Account" -> addAccount(Account(id, value("Name"), amount()))
                    "Category" -> addCategory(id, value("Name"))
                    "Transaction" -> {
                        val type = TransactionType.valueOf(value("Type"))
                        val account = current.accounts.first { it.id == value("Account") }
                        addTransaction(Transaction(id, type, account.id,
                            FinanceInput.money(value("Amount"), account.currency), FinanceInput.date(value("Date")),
                            categoryId = if (type == TransactionType.EXPENSE) value("Category") else null,
                            destinationAccountId = if (type == TransactionType.TRANSFER) value("Destination") else null,
                            note = value("Note")))
                    }
                    "Goal" -> addGoal(SavingsGoal(id, value("Name"), amount(), FinanceInput.date(value("Deadline"))))
                    "Budget" -> addBudget(CategoryBudget(id, value("Category"), amount(), FinanceInput.date(value("Start")), FinanceInput.date(value("End"))))
                    "Reservation" -> {
                        val goal = current.goals.first { it.id == value("Goal") }
                        val existing = current.reservations.firstOrNull { it.goalId == goal.id && it.accountId == value("Account") }
                        setReservationAmount(existing?.id ?: id, GoalReservation(goal.id, value("Account"), FinanceInput.money(value("Amount"), goal.currency)))
                    }
                }
            }, onSuccess = { form = null })
        }
    } }
}

private fun Money.display() = "$currency ${toDecimal().toPlainString()}"

@Composable
private fun Dashboard(snapshot: FinanceSnapshot) {
    if (snapshot.accounts.isEmpty()) {
        Text("Welcome. Add an account to start tracking your money.")
        return
    }
    val ledger = snapshot.ledger()
    val today = LocalDate.now()
    snapshot.accounts.map { it.currency }.distinct().forEach { currency ->
        val flow = ledger.cashFlow(currency, today.withDayOfMonth(1), today)
        RecordCard("Total balance", ledger.totalBalance(currency).display())
        RecordCard("This month · $currency", "Income: ${flow.income.display()}\nExpenses: ${flow.expenses.display()}\nNet: ${flow.net.display()}")
        val insight = FinancialCoach().summarize(ledger, snapshot.planner(), snapshot.budgets.map { it.toDomain() }, currency, YearMonth.from(today))
        RecordCard("Computed insight · no AI", "Calendar-month net: ${insight.netCashFlow.display()}\nOver-budget plans: ${insight.budgets.count { it.isExceeded }}\nSavings goals: ${insight.goals.size}")
    }
    Text("Internal transfers are excluded from income and expenses.")
}

@Composable
private fun RecordCard(title: String, detail: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail)
        }
    }
}

@Composable
private fun EntryDialog(kind: String, snapshot: FinanceSnapshot, busy: Boolean, error: String?, onDismiss: () -> Unit, onSave: (Map<String, String>) -> Unit) {
    val today = LocalDate.now()
    val accounts = snapshot.accounts.map { it.id to "${it.name} (${it.currency})" }
    val categories = snapshot.categories.map { it.id to it.name }
    val goals = snapshot.goals.map { it.id to "${it.name} (${it.currency})" }
    val fields = when (kind) {
        "Account" -> listOf("Name", "Amount", "Currency")
        "Category" -> listOf("Name")
        "Transaction" -> listOf("Type", "Account", "Amount", "Date", "Category", "Destination", "Note")
        "Goal" -> listOf("Name", "Amount", "Currency", "Deadline")
        "Budget" -> listOf("Category", "Amount", "Currency", "Start", "End")
        else -> listOf("Goal", "Account", "Amount")
    }
    var values by rememberSaveable(kind) { mutableStateOf(hashMapOf(
        "Currency" to "USD", "Amount" to "", "Type" to "INCOME", "Date" to today.toString(),
        "Start" to today.withDayOfMonth(1).toString(), "End" to today.withDayOfMonth(today.lengthOfMonth()).toString(),
        "Deadline" to today.plusMonths(6).toString(), "Account" to accounts.firstOrNull()?.first.orEmpty(),
        "Destination" to accounts.getOrNull(1)?.first.orEmpty(), "Category" to categories.firstOrNull()?.first.orEmpty(),
        "Goal" to goals.firstOrNull()?.first.orEmpty(),
    )) }
    val context = LocalContext.current
    val speech = remember(context) { OnDeviceSpeechInput(context) }
    var speechError by remember { mutableStateOf<String?>(null) }
    var listening by remember { mutableStateOf(false) }
    DisposableEffect(speech) { onDispose { speech.close() } }
    fun dictate() {
        listening = true
        speechError = null
        speech.start(onResult = { draft ->
            listening = false
            values = HashMap(values).also { it["Note"] = draft }
        }, onError = { message -> listening = false; speechError = message })
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) dictate() else speechError = "Microphone permission was not granted. You can type instead."
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add ${kind.lowercase()}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (kind == "Account") Text("Amount is the opening balance, before recorded movements.")
                fields.forEach { field ->
                    val visible = field != "Category" || kind != "Transaction" || values["Type"] == "EXPENSE"
                    val destinationVisible = field != "Destination" || values["Type"] == "TRANSFER"
                    if (visible && destinationVisible) {
                        val options = when (field) {
                            "Type" -> TransactionType.entries.map { it.name to it.name.lowercase().replaceFirstChar(Char::uppercase) }
                            "Account" -> accounts
                            "Destination" -> accounts.filter { it.first != values["Account"] }
                            "Category" -> categories
                            "Goal" -> goals
                            else -> null
                        }
                        fun update(value: String) { values = HashMap(values).also { it[field] = value } }
                        if (options != null) ChoiceField(field, values[field].orEmpty(), options, !busy, ::update)
                        else OutlinedTextField(value = values[field].orEmpty(), onValueChange = ::update,
                            enabled = !busy, label = { Text(if (field == "Amount") "Amount (e.g. 12.34)" else field) },
                            singleLine = field != "Note", modifier = Modifier.fillMaxWidth(),
                            supportingText = if (field in listOf("Date", "Deadline", "Start", "End")) { { Text("YYYY-MM-DD") } } else null)
                    }
                }
                if (kind == "Transaction" && speech.isAvailable) {
                    TextButton(onClick = {
                        if (listening) { speech.stop() }
                        else if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) dictate()
                        else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    }, enabled = !busy) { Text(if (listening) "Finish dictation" else "Dictate note (on-device)") }
                    Text("Dictation only edits the note. Review it before saving.", style = MaterialTheme.typography.bodySmall)
                    speechError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                Text("Save records this entry on this device.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(values) }, enabled = !busy) { Text(if (busy) "Saving…" else "Save") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } })
}

@Composable
private fun ChoiceField(label: String, value: String, options: List<Pair<String, String>>, enabled: Boolean, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled && options.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${options.firstOrNull { it.first == value }?.second ?: "Select"}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, title) -> DropdownMenuItem(text = { Text(title) }, onClick = { onChange(id); expanded = false }) }
        }
    }
}
