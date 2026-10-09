package com.kevinfernandez.appfinanciero.ui

import com.kevinfernandez.appfinanciero.domain.Money
import java.time.LocalDate
import java.util.Locale

object FinanceInput {
    fun money(value: String, currency: String): Money = Money.parse(value.trim(), currency.trim().uppercase(Locale.ROOT))
    fun date(value: String): LocalDate = LocalDate.parse(value.trim())
}
