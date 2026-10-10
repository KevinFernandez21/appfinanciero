package com.kevinfernandez.appfinanciero.ui

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.kevinfernandez.appfinanciero.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

class FirstRunJourneyTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        private val context get() = ApplicationProvider.getApplicationContext<Context>()
        override fun before() {
            context.deleteDatabase("finance.db")
            context.getSharedPreferences("app_lock", 0).edit().putBoolean("enabled", false).commit()
        }
        override fun after() {
            context.deleteDatabase("finance.db")
            context.getSharedPreferences("app_lock", 0).edit().clear().commit()
        }
    }).around(compose)

    @Test fun emptyLedgerToDailyPlanSurvivesRecreation() {
        awaitText("Set up your first account")
        click("Set up your first account")
        fill("Name", "Everyday")
        fill("Amount", "1000")
        fill("Currency", "JPY")
        compose.activityRule.scenario.recreate()
        awaitText("Add account")
        compose.onNode(hasText("Name") and hasSetTextAction()).assertTextContains("Everyday")
        compose.onNode(hasText("Amount") and hasSetTextAction()).assertTextContains("1000")
        compose.onNode(hasText("Currency") and hasSetTextAction()).assertTextContains("JPY")
        save()
        click("Accounts")
        click("Add account")
        fill("Name", "Savings")
        fill("Amount", "0")
        compose.onNode(hasText("Currency") and hasSetTextAction()).assertTextContains("JPY")
        save()
        click("Add category")
        fill("Name", "Food")
        save()
        click("Activity")
        movement("Income", "200")
        movement("Expense", "50")
        movement("Transfer", "100")
        click("Plans")
        click("Add budget")
        fill("Amount", "100")
        compose.onNode(hasText("Currency") and hasSetTextAction()).assertTextContains("JPY")
        save()
        click("Add goal")
        fill("Name", "Emergency")
        fill("Amount", "500")
        save()
        click("Reserve for a goal")
        fill("Amount", "200")
        save()
        compose.onNodeWithText("Reserved: JPY 200 / JPY 500", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Spent: JPY 50 / JPY 100", substring = true).performScrollTo().assertIsDisplayed()
        click("Accounts")
        compose.onNodeWithText("Balance: JPY 1050").assertIsDisplayed()
        compose.onNodeWithText("Balance: JPY 100").assertIsDisplayed()
        click("Dashboard")
        compose.activityRule.scenario.recreate()
        awaitText("Total balance")
        compose.onNodeWithText("JPY 1150").assertIsDisplayed()
        compose.onNodeWithText("Income: JPY 200\nExpenses: JPY 50\nNet: JPY 150").assertIsDisplayed()
    }

    private fun movement(type: String, amount: String) {
        click("Record movement")
        if (type != "Income") {
            click("Type: Income")
            click(type)
        }
        fill("Amount", amount)
        save()
    }
    private fun fill(label: String, value: String) {
        compose.onNode(hasText(label) and hasSetTextAction()).performScrollTo().performTextReplacement(value)
    }
    private fun click(text: String) {
        val node = compose.onNodeWithText(text)
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
    }
    private fun save() {
        click("Save")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Save").fetchSemanticsNodes().isEmpty() }
    }
    private fun awaitText(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
