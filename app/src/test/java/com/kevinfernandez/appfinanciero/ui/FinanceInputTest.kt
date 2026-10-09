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
    @Test fun parsesIsoDate() { assertEquals(LocalDate.of(2026, 10, 9), FinanceInput.date(" 2026-10-09 ")) }
    @Test(expected = java.time.format.DateTimeParseException::class)
    fun rejectsInvalidDate() { FinanceInput.date("2026-02-30") }
}
