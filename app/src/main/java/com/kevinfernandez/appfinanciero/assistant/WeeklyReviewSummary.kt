package com.kevinfernandez.appfinanciero.assistant

import com.kevinfernandez.appfinanciero.domain.CashFlow
import com.kevinfernandez.appfinanciero.domain.FinanceLedger
import java.time.LocalDate

data class WeeklyReviewSummary(val start: LocalDate, val end: LocalDate, val cashFlow: CashFlow) {
    companion object {
        fun from(ledger: FinanceLedger, currency: String, today: LocalDate): WeeklyReviewSummary {
            val start = today.minusDays(6)
            return WeeklyReviewSummary(start, today, ledger.cashFlow(currency, start, today))
        }
    }
}
