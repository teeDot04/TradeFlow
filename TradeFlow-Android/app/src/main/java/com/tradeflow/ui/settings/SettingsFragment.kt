package com.tradeflow.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.tradeflow.R
import com.tradeflow.databinding.FragmentSettingsBinding
import com.tradeflow.security.SecureCredentialStore
import com.tradeflow.service.TradingForegroundService
import com.tradeflow.utils.ThemeManager

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: SettingsViewModel
    private lateinit var credentialStore: SecureCredentialStore

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
        credentialStore = SecureCredentialStore(requireContext())

        setupThemeSelector()
        prefillCredentialFields()
        wireCredentialButtons()
        wireDangerZone()
    }

    private fun prefillCredentialFields() {
        binding.editOkxKey.setText(credentialStore.okxKey())
        binding.editOkxSecret.setText(credentialStore.okxSecret())
        binding.editOkxPassphrase.setText(credentialStore.okxPassphrase())
        binding.editDeepseekKey.setText(credentialStore.deepseekKey())
    }

    private fun wireCredentialButtons() {
        binding.btnSaveCredentials.setOnClickListener {
            val okxKey = binding.editOkxKey.text?.toString().orEmpty().trim()
            val okxSecret = binding.editOkxSecret.text?.toString().orEmpty().trim()
            val okxPass = binding.editOkxPassphrase.text?.toString().orEmpty().trim()
            val ds = binding.editDeepseekKey.text?.toString().orEmpty().trim()

            if (okxKey.isEmpty() || okxSecret.isEmpty() || okxPass.isEmpty() || ds.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    R.string.credentials_required,
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            credentialStore.saveOkxKey(okxKey)
            credentialStore.saveOkxSecret(okxSecret)
            credentialStore.saveOkxPassphrase(okxPass)
            credentialStore.saveDeepseekKey(ds)

            Toast.makeText(
                requireContext(),
                R.string.credentials_saved,
                Toast.LENGTH_SHORT
            ).show()

            // Restart the sentry so the new keys are picked up immediately.
            TradingForegroundService.stop(requireContext())
            TradingForegroundService.start(requireContext())
        }
    }

    private fun wireDangerZone() {
        binding.btnClearCredentials.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.clear_credentials_title)
                .setMessage(R.string.clear_credentials_confirm)
                .setPositiveButton(R.string.delete) { _, _ ->
                    credentialStore.clearAll()
                    binding.editOkxKey.setText("")
                    binding.editOkxSecret.setText("")
                    binding.editOkxPassphrase.setText("")
                    binding.editDeepseekKey.setText("")
                    TradingForegroundService.stop(requireContext())
                    Toast.makeText(
                        requireContext(),
                        R.string.credentials_cleared,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun setupThemeSelector() {
        when (ThemeManager.getTheme(requireContext())) {
            ThemeManager.THEME_LIGHT -> binding.radioLight.isChecked = true
            ThemeManager.THEME_DARK -> binding.radioDark.isChecked = true
            ThemeManager.THEME_SYSTEM -> binding.radioSystem.isChecked = true
        }

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
