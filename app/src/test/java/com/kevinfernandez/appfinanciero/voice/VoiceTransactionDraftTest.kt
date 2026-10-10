package com.kevinfernandez.appfinanciero.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceTransactionDraftTest {
    @Test fun preparesAnExpenseWithoutInferringAccountOrCategory() {
        assertEquals(
            VoiceTransactionDraft("EXPENSE", "12000.00", "café"),
            VoiceTransactionDraft.parse("Gasto 12000 en café", "COP"),
        )
    }

    @Test fun acceptsExactDecimalIncomeForSelectedCurrency() {
        assertEquals(
            VoiceTransactionDraft("INCOME", "12.50", "venta"),
            VoiceTransactionDraft.parse("Ingreso 12,50 por venta", "USD"),
        )
    }

    @Test fun rejectsZeroAndUnsupportedPrecision() {
        assertNull(VoiceTransactionDraft.parse("gasto 0 en café", "COP"))
        assertNull(VoiceTransactionDraft.parse("ingreso 12,345 por venta", "USD"))
        assertNull(VoiceTransactionDraft.parse("gasto 12,50 en café", "JPY"))
        assertNull(VoiceTransactionDraft.parse("gasto 92233720368547759", "USD"))
    }

    @Test fun rejectsAmbiguousOrUnsupportedCommands() {
        assertNull(VoiceTransactionDraft.parse("gasto mil en café", "COP"))
        assertNull(VoiceTransactionDraft.parse("transferencia 100", "USD"))
        assertNull(VoiceTransactionDraft.parse("gasto 1.000 en café", "COP"))
        assertNull(VoiceTransactionDraft.parse("gasto 12 en café", ""))
        assertNull(VoiceTransactionDraft.parse("gasto 12 en café", "ZZZ"))
    }
}
