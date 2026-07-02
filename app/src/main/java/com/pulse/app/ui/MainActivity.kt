package com.pulse.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequestBuilder
import com.pulse.app.R
import com.pulse.app.databinding.ActivityMainBinding
import com.pulse.app.work.PendingSignalsWorker
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private val authViewModel: com.pulse.app.auth.AuthViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op; we vibrate silently either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        maybeRequestNotificationPermission()
        observeNavigation()
        handleDeepLink(intent)
        schedulePendingSync()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: android.content.Intent?) {
        if (intent?.action == android.content.Intent.ACTION_VIEW) {
            val data = intent.data
            if (data?.scheme == "https" && data.host == "pulse.app" && data.path?.startsWith("/invite") == true) {
                val token = data.lastPathSegment
                if (token != null) {
                    viewModel.consumeInviteLink(token)
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(com.pulse.app.R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            com.pulse.app.R.id.action_logout -> {
                authViewModel.logout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun schedulePendingSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodic = PeriodicWorkRequestBuilder<PendingSignalsWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "pulse_pending_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            periodic
        )

        val oneTime = OneTimeWorkRequestBuilder<PendingSignalsWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            "pulse_pending_sync_now",
            ExistingWorkPolicy.REPLACE,
            oneTime
        )
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun observeNavigation() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        val navController = navHostFragment.navController

        viewModel.navEvents.observe(this) { event ->
            when (event.getContentIfNotHandled()) {
                MainViewModel.Destination.PAIR -> navController.navigate(R.id.pairFragment)
                MainViewModel.Destination.SIGNAL -> navController.navigate(R.id.signalFragment)
                MainViewModel.Destination.LOGIN -> navController.navigate(R.id.loginFragment)
                MainViewModel.Destination.SIGNUP -> navController.navigate(R.id.signupFragment)
                null -> {}
            }
        }

        viewModel.toast.observe(this) { msg ->
            msg?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        }

        // Observe global auth state to handle logout navigation and clear backstack
        authViewModel.state.observe(this) { state ->
            when (state) {
                is com.pulse.app.auth.AuthState.Unauthenticated -> {
                    navController.popBackStack(navController.graph.startDestinationId, true)
                    navController.navigate(R.id.loginFragment)
                }
                else -> {
                    // no-op
                }
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
