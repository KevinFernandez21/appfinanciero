package com.kevinfernandez.appfinanciero

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.first
import com.kevinfernandez.appfinanciero.lock.*
import com.kevinfernandez.appfinanciero.ui.FinanceDocumentPickers
import com.kevinfernandez.appfinanciero.ui.PendingDocumentResults
import com.kevinfernandez.appfinanciero.ui.handleDocumentResult
import com.kevinfernandez.appfinanciero.ui.FinanceScreen
import com.kevinfernandez.appfinanciero.ui.FinanceViewModel

class MainActivity : FragmentActivity() {
    private lateinit var lock: AppLockViewModel
    private lateinit var authenticator: DeviceAuthenticator
    private lateinit var documents: FinanceDocumentPickers
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Exclude financial UI and authentication screens from screenshots and Recents.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        lock = ViewModelProvider(this)[AppLockViewModel::class.java]
        authenticator = DeviceAuthenticator(this, lock::complete)
        val pendingDocuments = ViewModelProvider(this)[PendingDocumentResults::class.java]
        documents = FinanceDocumentPickers(activityResultRegistry, pendingDocuments::receive)
        setContent {
            MaterialTheme {
                val visible = lock.revision.let { lock.policy.canShowFinance }
                val finance = if (visible) financeModel() else null
                LaunchedEffect(visible, pendingDocuments.pending) {
                    if (visible && pendingDocuments.pending.isNotEmpty()) {
                        requireNotNull(finance).busy.first { !it }
                        pendingDocuments.deliverNext {
                            lock.policy.canShowFinance && handleDocumentResult(finance, contentResolver, it)
                        }
                    }
                }
                LockGate(visible, lock.policy.authenticating, lock.message, { authenticate(false) }) {
                    // Do not even construct the finance ViewModel/database until the gate opens.
                    FinanceScreen(requireNotNull(finance), documents) {
                        LockSettings(lock.policy.enabled) {
                            if (lock.policy.enabled) lock.disable() else authenticate(true)
                        }
                    }
                }
            }
        }
    }
    private fun financeModel() = ViewModelProvider(this, FinanceViewModel.Factory(applicationContext))[FinanceViewModel::class.java]
    private fun authenticate(enable: Boolean) {
        lock.request(enable)?.let(authenticator::authenticate)
    }
    override fun onStop() {
        // Credential activity is an intentional system handoff, not an unlocked session.
        if (!authenticator.handingOffCredential) {
            lock.stopped(isChangingConfigurations)
            authenticator.cancel()
        }
        super.onStop()
    }
    override fun onDestroy() {
        // Rotation retains a previously unlocked ViewModel, never an in-flight callback.
        lock.stopped(isChangingConfigurations)
        authenticator.cancel()
        documents.close()
        super.onDestroy()
    }
}
