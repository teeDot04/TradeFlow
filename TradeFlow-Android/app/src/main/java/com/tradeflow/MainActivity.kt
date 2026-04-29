package com.tradeflow

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.tradeflow.databinding.ActivityMainBinding
import com.tradeflow.ui.analytics.AnalyticsFragment
import com.tradeflow.ui.dashboard.DashboardFragment
import com.tradeflow.ui.settings.SettingsFragment
import com.tradeflow.ui.trades.TradesFragment
import com.tradeflow.utils.ThemeManager

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply saved theme before calling super.onCreate()
        ThemeManager.applyTheme(ThemeManager.getTheme(this))
        
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        
        // Set up bottom navigation
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_dashboard -> {
                    loadFragment(DashboardFragment())
                    binding.toolbar.title = getString(R.string.tab_dashboard)
                    true
                }
                R.id.navigation_analytics -> {
                    loadFragment(AnalyticsFragment())
                    binding.toolbar.title = getString(R.string.tab_analytics)
                    true
                }
                R.id.navigation_trades -> {
                    loadFragment(TradesFragment())
                    binding.toolbar.title = getString(R.string.tab_trades)
                    true
                }
                R.id.navigation_settings -> {
                    loadFragment(SettingsFragment())
                    binding.toolbar.title = getString(R.string.tab_settings)
                    true
                }
                else -> false
            }
        }
        
        // Load default fragment
        if (savedInstanceState == null) {
            binding.bottomNavigation.selectedItemId = R.id.navigation_dashboard
        }
        
        checkBatteryOptimizationsAndStartService()
    }
    
    private fun checkBatteryOptimizationsAndStartService() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = android.net.Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }
        
        val serviceIntent = android.content.Intent(this, TradingForegroundService::class.java)
        startForegroundService(serviceIntent)
    }
    
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .commit()
    }
}
