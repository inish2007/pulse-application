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
import android.view.View
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
import com.google.android.material.snackbar.Snackbar
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
        setupBottomNavigation()
        schedulePendingSync()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
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
            msg?.let {
                val message = it.getContentIfNotHandled() ?: return@let
                val snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
                snackbar.setBackgroundTint(ContextCompat.getColor(this, R.color.md_theme_surface))
                snackbar.setTextColor(ContextCompat.getColor(this, R.color.md_theme_onSurface))
                snackbar.setAnchorView(if (binding.bottomNavBar.visibility == View.VISIBLE) binding.bottomNavBar else null)
                snackbar.show()
            }
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

    private fun setupBottomNavigation() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        val navController = navHostFragment.navController

        // Hide bottom nav on auth screens
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val authScreens = setOf(
                R.id.authGateFragment,
                R.id.loginFragment,
                R.id.signupFragment,
                R.id.pairFragment
            )
            val shouldShow = destination.id !in authScreens
            
            animateBottomNavVisibility(shouldShow)
            updateBottomNavSelectedState(destination.id)
        }

        // Tab click listeners with NavOptions to avoid duplicate fragments
        val navOptions = androidx.navigation.NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setRestoreState(true)
            .setPopUpTo(R.id.homeFragment, false, true)
            .setEnterAnim(R.anim.fade_in)
            .setExitAnim(R.anim.fade_out)
            .setPopEnterAnim(R.anim.fade_in)
            .setPopExitAnim(R.anim.fade_out)
            .build()

        binding.navHome.setOnClickListener {
            navController.navigate(R.id.homeFragment, null, navOptions)
        }
        binding.navHistory.setOnClickListener {
            navController.navigate(R.id.historyFragment, null, navOptions)
        }
        binding.navProfile.setOnClickListener {
            navController.navigate(R.id.profileFragment, null, navOptions)
        }
        binding.fabSendSignal.setOnClickListener {
            navController.navigate(R.id.signalFragment, null, navOptions)
        }
    }

    private fun animateBottomNavVisibility(show: Boolean) {
        val targetTranslationY = if (show) 0f else 200f
        val targetAlpha = if (show) 1f else 0f
        val visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
        
        if (show && binding.bottomNavBar.visibility == android.view.View.GONE) {
            binding.bottomNavBar.visibility = android.view.View.VISIBLE
            binding.fabSendSignal.visibility = android.view.View.VISIBLE
            binding.bottomNavBar.translationY = 200f
            binding.fabSendSignal.translationY = 200f
            binding.bottomNavBar.alpha = 0f
            binding.fabSendSignal.alpha = 0f
        }

        binding.bottomNavBar.animate()
            .translationY(targetTranslationY)
            .alpha(targetAlpha)
            .setDuration(300)
            .withEndAction {
                if (!show) {
                    binding.bottomNavBar.visibility = android.view.View.GONE
                }
            }
            .start()

        binding.fabSendSignal.animate()
            .translationY(targetTranslationY)
            .alpha(targetAlpha)
            .setDuration(300)
            .withEndAction {
                if (!show) {
                    binding.fabSendSignal.visibility = android.view.View.GONE
                }
            }
            .start()
    }

    private fun updateBottomNavSelectedState(destinationId: Int) {
        val colorActive = ContextCompat.getColor(this, R.color.md_theme_accent)
        val colorInactive = ContextCompat.getColor(this, R.color.md_theme_onSurface)

        binding.ivNavHome.imageTintList = android.content.res.ColorStateList.valueOf(
            if (destinationId == R.id.homeFragment) colorActive else colorInactive
        )
        binding.ivNavHistory.imageTintList = android.content.res.ColorStateList.valueOf(
            if (destinationId == R.id.historyFragment) colorActive else colorInactive
        )
        binding.ivNavProfile.imageTintList = android.content.res.ColorStateList.valueOf(
            if (destinationId == R.id.profileFragment) colorActive else colorInactive
        )
        
        // Scale animation for active tab
        val scaleHome = if (destinationId == R.id.homeFragment) 1.2f else 1.0f
        binding.ivNavHome.animate().scaleX(scaleHome).scaleY(scaleHome).setDuration(200).start()
        
        val scaleHistory = if (destinationId == R.id.historyFragment) 1.2f else 1.0f
        binding.ivNavHistory.animate().scaleX(scaleHistory).scaleY(scaleHistory).setDuration(200).start()
        
        val scaleProfile = if (destinationId == R.id.profileFragment) 1.2f else 1.0f
        binding.ivNavProfile.animate().scaleX(scaleProfile).scaleY(scaleProfile).setDuration(200).start()
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
