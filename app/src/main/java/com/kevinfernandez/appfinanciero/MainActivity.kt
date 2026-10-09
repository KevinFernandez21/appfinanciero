package com.kevinfernandez.appfinanciero

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.ViewModelProvider
import com.kevinfernandez.appfinanciero.ui.FinanceScreen
import com.kevinfernandez.appfinanciero.ui.FinanceViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val viewModel = ViewModelProvider(this, FinanceViewModel.Factory(applicationContext))[FinanceViewModel::class.java]
        setContent {
            MaterialTheme {
                FinanceScreen(viewModel)
            }
        }
    }
}
