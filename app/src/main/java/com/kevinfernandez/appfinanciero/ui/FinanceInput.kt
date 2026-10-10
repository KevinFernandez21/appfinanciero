package com.kevinfernandez.appfinanciero.ui

import com.kevinfernandez.appfinanciero.domain.Money
import com.kevinfernandez.appfinanciero.voice.VoiceTransactionDraft
import java.time.LocalDate
import java.util.Locale

object FinanceInput {
    fun money(value: String, currency: String): Money = Money.parse(value.trim(), currency.trim().uppercase(Locale.ROOT))
    fun defaultCurrency(accountCurrency: String?, locale: Locale): String {
        val candidate = accountCurrency ?: try {
            java.util.Currency.getInstance(locale)?.currencyCode
        } catch (_: IllegalArgumentException) { null }
        return candidate?.takeIf { code ->
            try { Money.zero(code); true } catch (_: IllegalArgumentException) { false }
        }.orEmpty()
    }
    fun date(value: String): LocalDate = LocalDate.parse(value.trim())

    fun applyVoiceDraft(values: Map<String, String>, draft: VoiceTransactionDraft): Map<String, String> =
        values.toMutableMap().apply {
            this["Type"] = draft.type
            this["Amount"] = draft.amount
            if (draft.note.isNotBlank()) this["Note"] = draft.note
        }
}
