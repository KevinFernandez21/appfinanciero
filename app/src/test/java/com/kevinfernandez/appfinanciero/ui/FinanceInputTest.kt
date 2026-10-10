package com.kevinfernandez.appfinanciero.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class FinanceInputTest {
    @Test fun parsesMoneyExactlyAndNormalizesCurrency() {
        assertEquals(1234L, FinanceInput.money(" 12.34 ", " usd ").minorUnits)
        assertEquals("USD", FinanceInput.money("12.34", "usd").currency)
    }
    @Test(expected = ArithmeticException::class)
    fun rejectsFractionalMinorUnits() { FinanceInput.money("1.001", "USD") }
    @Test fun defaultsToAccountCurrencyBeforeDeviceLocale() {
        assertEquals("JPY", FinanceInput.defaultCurrency("JPY", java.util.Locale.US))
    }
    @Test fun defaultsToDeviceCurrencyOnFirstRun() {
        assertEquals("COP", FinanceInput.defaultCurrency(null, java.util.Locale.forLanguageTag("es-CO")))
    }
    @Test fun unknownLocaleDoesNotInventCurrency() {
        assertEquals("", FinanceInput.defaultCurrency(null, java.util.Locale.ROOT))
        assertEquals("", FinanceInput.defaultCurrency(null, java.util.Locale.forLanguageTag("en-AQ")))
    }
    @Test fun preservesZeroAndThreeDecimalMinorUnits() {
        assertEquals(12L, FinanceInput.money("12", "JPY").minorUnits)
        assertEquals(12345L, FinanceInput.money("12.345", "KWD").minorUnits)
    }
    @Test(expected = ArithmeticException::class)
    fun rejectsFractionForZeroDecimalCurrency() { FinanceInput.money("12.1", "JPY") }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownIsoCurrency() { FinanceInput.money("1", "ZZZ") }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsCurrencyWithoutMinorUnits() { FinanceInput.money("1", "XXX") }
    @Test fun parsesIsoDate() { assertEquals(LocalDate.of(2026, 10, 9), FinanceInput.date(" 2026-10-09 ")) }
    @Test(expected = java.time.format.DateTimeParseException::class)
    fun rejectsInvalidDate() { FinanceInput.date("2026-02-30") }
}
