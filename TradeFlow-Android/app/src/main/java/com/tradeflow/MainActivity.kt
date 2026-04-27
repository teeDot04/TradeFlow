package com.tradeflow

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tradeflow.databinding.ActivityMainBinding
import com.tradeflow.service.TradingForegroundService
import com.tradeflow.ui.analytics.AnalyticsFragment
import com.tradeflow.ui.dashboard.DashboardFragment
import com.tradeflow.ui.settings.SettingsFragment
import com.tradeflow.ui.trades.TradesFragment
import com.tradeflow.utils.ThemeManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Whether granted or not, we still want the service running; the
            // notification will simply be silent if the user denied.
            startSentryService()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(ThemeManager.getTheme(this))
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_dashboard -> {
                    loadFragment(DashboardFragment()); binding.toolbar.title = getString(R.string.tab_dashboard); true
                }
                R.id.navigation_analytics -> {
                    loadFragment(AnalyticsFragment()); binding.toolbar.title = getString(R.string.tab_analytics); true
                }
                R.id.navigation_trades -> {
                    loadFragment(TradesFragment()); binding.toolbar.title = getString(R.string.tab_trades); true
                }
                R.id.navigation_settings -> {
                    loadFragment(SettingsFragment()); binding.toolbar.title = getString(R.string.tab_settings); true
                }
                else -> false
            }
        }

        if (savedInstanceState == null) {
            binding.bottomNavigation.selectedItemId = R.id.navigation_dashboard
        }

        requestBatteryOptimizationsExemption()
        ensureNotificationPermissionThenStartService()
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .commit()
    }

    /**
     * Pop the system dialog asking the user to whitelist TradeFlow from
     * battery optimizations. On ColorOS / OxygenOS / MIUI this is the single
     * most important runtime mitigation against background process death.
     */
    private fun requestBatteryOptimizationsExemption() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (_: Throwable) {
            // Some OEMs hide this intent; fall back to the generic settings page.
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Throwable) { /* no-op */ }
        }
    }

    private fun ensureNotificationPermissionThenStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        startSentryService()
    }

    private fun startSentryService() {
        TradingForegroundService.start(this)
    }
}
