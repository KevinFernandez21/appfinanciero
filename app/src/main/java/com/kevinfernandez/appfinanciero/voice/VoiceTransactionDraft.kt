package com.kevinfernandez.appfinanciero.voice

import com.kevinfernandez.appfinanciero.domain.Money
import java.util.Locale

/** A constrained interpretation of speech. It never selects an account or writes to the ledger. */
data class VoiceTransactionDraft(val type: String, val amount: String, val note: String) {
    companion object {
        private val command = Regex("^(gasto|ingreso)\\s+([0-9]+(?:[.,][0-9]{1,2})?)(?:\\s+(?:en|por)\\s+(.+))?$", RegexOption.IGNORE_CASE)

        fun parse(transcript: String, currency: String): VoiceTransactionDraft? {
            val match = command.matchEntire(transcript.trim()) ?: return null
            val code = currency.trim().uppercase(Locale.ROOT)
            val amount = runCatching { Money.parse(match.groupValues[2].replace(',', '.'), code) }.getOrNull() ?: return null
            if (amount.minorUnits <= 0) return null
            return VoiceTransactionDraft(
                if (match.groupValues[1].equals("gasto", ignoreCase = true)) "EXPENSE" else "INCOME",
                amount.toDecimal().toPlainString(),
                match.groupValues[3].trim(),
            )
        }
    }
}
