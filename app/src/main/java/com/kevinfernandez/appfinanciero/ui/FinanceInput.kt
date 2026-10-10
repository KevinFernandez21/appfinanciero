package com.kevinfernandez.appfinanciero.ui

import com.kevinfernandez.appfinanciero.domain.Money
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
}
