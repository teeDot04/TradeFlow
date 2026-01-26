package com.tradeflow.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.tradeflow.R
import com.tradeflow.databinding.FragmentSettingsBinding
import com.tradeflow.utils.CsvExporter
import com.tradeflow.utils.ThemeManager

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: SettingsViewModel
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(this)[SettingsViewModel::class.java]
        
        setupThemeSelector()
        setupButtons()
    }
    
    private fun setupThemeSelector() {
        // Set current theme
        when (ThemeManager.getTheme(requireContext())) {
            ThemeManager.THEME_LIGHT -> binding.radioLight.isChecked = true
            ThemeManager.THEME_DARK -> binding.radioDark.isChecked = true
            ThemeManager.THEME_SYSTEM -> binding.radioSystem.isChecked = true
        }
        
        // Theme change listener
        binding.themeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val theme = when (checkedId) {
                R.id.radioLight -> ThemeManager.THEME_LIGHT
                R.id.radioDark -> ThemeManager.THEME_DARK
                R.id.radioSystem -> ThemeManager.THEME_SYSTEM
                else -> ThemeManager.THEME_SYSTEM
            }
            
            ThemeManager.saveTheme(requireContext(), theme)
            ThemeManager.applyTheme(theme)
            requireActivity().recreate()
        }
    }
    
    private fun setupButtons() {
        // Export CSV
        binding.btnExportCsv.setOnClickListener {
            viewModel.allTrades.observe(viewLifecycleOwner) { trades ->
                if (trades.isNotEmpty()) {
                    val uri = CsvExporter.exportTradesToCsv(requireContext(), trades)
                    
                    if (uri != null) {
                        // Share the file
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
                        
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.export_success),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.export_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.no_trades),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        
        // Clear all data
        binding.btnClearData.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.clear_data))
                .setMessage(getString(R.string.clear_data_confirm))
                .setPositiveButton(getString(R.string.delete)) { _, _ ->
                    viewModel.clearAllData()
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.data_cleared),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
