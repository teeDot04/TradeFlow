package com.tradeflow.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.tradeflow.R
import com.tradeflow.databinding.FragmentSettingsBinding
import com.tradeflow.utils.EncryptedPrefs
import com.tradeflow.utils.ThemeManager
import com.tradeflow.TradingForegroundService

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    
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
        
        setupThemeSelector()
        loadCredentials()
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

    private fun loadCredentials() {
        binding.etOkxApiKey.setText(EncryptedPrefs.getKey(requireContext(), EncryptedPrefs.KEY_OKX_API_KEY))
        binding.etOkxApiSecret.setText(EncryptedPrefs.getKey(requireContext(), EncryptedPrefs.KEY_OKX_API_SECRET))
        binding.etOkxPassphrase.setText(EncryptedPrefs.getKey(requireContext(), EncryptedPrefs.KEY_OKX_PASSPHRASE))
        binding.etDeepSeekApiKey.setText(EncryptedPrefs.getKey(requireContext(), EncryptedPrefs.KEY_DEEPSEEK_API_KEY))
        binding.etTavilyApiKey.setText(EncryptedPrefs.getKey(requireContext(), EncryptedPrefs.KEY_TAVILY_API_KEY))
    }
    
    private fun setupButtons() {
        binding.btnSaveCredentials.setOnClickListener {
            EncryptedPrefs.saveKey(requireContext(), EncryptedPrefs.KEY_OKX_API_KEY, binding.etOkxApiKey.text.toString())
            EncryptedPrefs.saveKey(requireContext(), EncryptedPrefs.KEY_OKX_API_SECRET, binding.etOkxApiSecret.text.toString())
            EncryptedPrefs.saveKey(requireContext(), EncryptedPrefs.KEY_OKX_PASSPHRASE, binding.etOkxPassphrase.text.toString())
            EncryptedPrefs.saveKey(requireContext(), EncryptedPrefs.KEY_DEEPSEEK_API_KEY, binding.etDeepSeekApiKey.text.toString())
            EncryptedPrefs.saveKey(requireContext(), EncryptedPrefs.KEY_TAVILY_API_KEY, binding.etTavilyApiKey.text.toString())
            
            Toast.makeText(requireContext(), "Credentials Saved. Restarting Agent...", Toast.LENGTH_SHORT).show()
            
            // Restart the service to apply new credentials in Python
            val intent = Intent(requireContext(), TradingForegroundService::class.java)
            requireContext().stopService(intent)
            requireContext().startForegroundService(intent)
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
