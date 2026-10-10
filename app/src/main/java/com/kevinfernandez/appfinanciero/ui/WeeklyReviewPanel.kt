package com.kevinfernandez.appfinanciero.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kevinfernandez.appfinanciero.assistant.WeeklyReviewSummary
import com.kevinfernandez.appfinanciero.data.FinanceSnapshot
import com.kevinfernandez.appfinanciero.domain.CashFlow
import com.kevinfernandez.appfinanciero.domain.Money
import java.time.LocalDate

/** Deterministic education from active recorded facts; saving requires an explicit action. */
@Composable
fun WeeklyReviewPanel(snapshot: FinanceSnapshot, busy: Boolean, error: String?,
    onClearError: () -> Unit, onSave: (WeeklyReviewSummary, String, () -> Unit) -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    var selectedCurrency by rememberSaveable { mutableStateOf<String?>(null) }
    var endDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    val ledger = snapshot.ledger()
    Text("Weekly review · no AI", style = MaterialTheme.typography.titleLarge)
    Text("Net = recorded income − recorded expenses. Opening balances are not income. Internal transfers are excluded; goal reservations are virtual and do not change cash flow.")
    Text("This explains your records, not financial advice. Missing movements are not estimated.")
    snapshot.accounts.map { it.currency }.distinct().sorted().forEach { currency ->
        val summary = WeeklyReviewSummary.from(ledger, currency, today)
        ReviewCard("Last 7 days · $currency", summary.start, summary.end, summary.cashFlow)
        OutlinedButton(onClick = {
            onClearError(); endDay = today.toEpochDay(); selectedCurrency = currency
        }, enabled = !busy) { Text("Review last 7 days · $currency") }
    }
    snapshot.weeklyReviews.sortedWith(compareByDescending<com.kevinfernandez.appfinanciero.data.WeeklyReviewEntity> { it.endEpochDay }.thenBy { it.id }).forEach { review ->
        ReviewCard("Recorded at review time · ${review.currency}", LocalDate.ofEpochDay(review.startEpochDay),
            LocalDate.ofEpochDay(review.endEpochDay), CashFlow(Money(review.incomeMinor, review.currency), Money(review.expensesMinor, review.currency)), review.note)
    }
    selectedCurrency?.let { currency ->
        val summary = WeeklyReviewSummary.from(ledger, currency, LocalDate.ofEpochDay(endDay))
        var note by rememberSaveable(currency, endDay) { mutableStateOf("") }
        fun dismiss() { if (!busy) { selectedCurrency = null; onClearError() } }
        AlertDialog(onDismissRequest = ::dismiss, title = { Text("Review last 7 days · $currency") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${summary.start} to ${summary.end} (inclusive)")
                    Text(summary.cashFlow.description())
                    Text("Save captures active ledger totals at save time. Later corrections do not rewrite this review. Your note stays on this device unless you export a backup.")
                    OutlinedTextField(note, { note = it }, enabled = !busy,
                        label = { Text("Reflection note (optional)") }, modifier = Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = { onSave(summary, note.trim()) { selectedCurrency = null } }, enabled = !busy) { Text(if (busy) "Saving…" else "Save review") } },
            dismissButton = { TextButton(onClick = ::dismiss, enabled = !busy) { Text("Cancel") } })
    }
}

@Composable
private fun ReviewCard(title: String, start: LocalDate, end: LocalDate, flow: CashFlow, note: String = "") {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text("$start to $end (inclusive)")
            Text(flow.description())
            if (note.isNotBlank()) Text(note)
        }
    }
}

private fun Money.reviewDisplay() = "$currency ${toDecimal().toPlainString()}"
private fun CashFlow.description() = "Recorded income: ${income.reviewDisplay()}\nRecorded expenses: ${expenses.reviewDisplay()}\nNet: ${net.reviewDisplay()}"
