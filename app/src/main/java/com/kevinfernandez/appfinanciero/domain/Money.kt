package com.kevinfernandez.appfinanciero.domain

import java.math.BigDecimal
import java.util.Currency

/** Exact signed money. Negative balances are allowed; transaction amounts must be positive. */
data class Money(val minorUnits: Long, val currency: String) {
    init { currencyDefinition(currency) }
    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return copy(minorUnits = Math.addExact(minorUnits, other.minorUnits))
    }
    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return copy(minorUnits = Math.subtractExact(minorUnits, other.minorUnits))
    }
    fun requireSameCurrency(other: Money) {
        require(currency == other.currency) { "Currency mismatch: $currency and ${other.currency}" }
    }
    fun toDecimal(): BigDecimal = BigDecimal.valueOf(minorUnits, currencyDefinition(currency).defaultFractionDigits)
    companion object {
        fun zero(currency: String) = Money(0, currency)
        fun parse(value: String, currency: String): Money = Money(
            BigDecimal(value).movePointRight(currencyDefinition(currency).defaultFractionDigits).longValueExact(), currency)
        private fun currencyDefinition(code: String): Currency {
            require(code.matches(Regex("[A-Z]{3}"))) { "Use an uppercase ISO currency code" }
            return Currency.getInstance(code).also {
                require(it.defaultFractionDigits >= 0) { "Currency must define minor units" }
            }
        }
    }
}

internal fun requireId(value: String) { require(value.isNotBlank()) { "Identifier cannot be blank" } }

data class Account(val id: String, val name: String, val openingBalance: Money) {
    init { requireId(id); require(name.isNotBlank()) { "Account name cannot be blank" } }
}
