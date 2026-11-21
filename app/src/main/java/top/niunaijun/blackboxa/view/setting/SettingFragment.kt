package top.niunaijun.blackboxa.view.setting

import android.net.VpnService
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.app.AppManager
import top.niunaijun.blackboxa.util.ContextUtil.openShortcutPermissionSettings
import top.niunaijun.blackboxa.util.toast
import top.niunaijun.blackboxa.view.gms.GmsManagerActivity

class SettingFragment : PreferenceFragmentCompat() {

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.setting, rootKey)

        initHomeShortcuts()
        initGms()

        bindEnvironmentToggle("root_hide", AppManager.mBlackBoxLoader.hideRoot()) {
            AppManager.mBlackBoxLoader.invalidHideRoot(it)
        }
        bindEnvironmentToggle("daemon_enable", AppManager.mBlackBoxLoader.daemonEnable()) {
            AppManager.mBlackBoxLoader.invalidDaemonEnable(it)
        }
        bindEnvironmentToggle("use_vpn_network", AppManager.mBlackBoxLoader.useVpnNetwork()) { enabled ->
            AppManager.mBlackBoxLoader.invalidUseVpnNetwork(enabled)
            if (enabled) {
                runCatching {
                    val vpnIntent = VpnService.prepare(requireContext())
                    if (vpnIntent != null) {
                        vpnPermissionLauncher.launch(vpnIntent)
                    }
                }
            }
        }
        bindEnvironmentToggle("disable_flag_secure", AppManager.mBlackBoxLoader.disableFlagSecure()) {
            AppManager.mBlackBoxLoader.invalidDisableFlagSecure(it)
        }
    }

    private fun initHomeShortcuts() {
        findPreference<SwitchPreferenceCompat>("auto_pin_shortcut")?.apply {
            isChecked = AppManager.mBlackBoxLoader.autoPinShortcut()
            setOnPreferenceChangeListener { _, newValue ->
                AppManager.mBlackBoxLoader.invalidAutoPinShortcut(newValue == true)
                true
            }
        }

        findPreference<Preference>("shortcut_permission")?.setOnPreferenceClickListener {
            requireContext().openShortcutPermissionSettings()
            true
        }
    }

    private fun initGms() {
        val gmsManagerPreference = findPreference<Preference>("gms_manager") ?: return
        if (BlackBoxCore.get().isSupportGms) {
            gmsManagerPreference.setOnPreferenceClickListener {
                GmsManagerActivity.start(requireContext())
                true
            }
        } else {
            gmsManagerPreference.summary = getString(R.string.no_gms)
            gmsManagerPreference.isEnabled = false
        }
    }

    private fun bindEnvironmentToggle(
        key: String,
        initialValue: Boolean,
        onPersist: (Boolean) -> Unit
    ) {
        val pref = findPreference<SwitchPreferenceCompat>(key) ?: return
        pref.isChecked = initialValue
        pref.setOnPreferenceChangeListener { _, newValue ->
            onPersist(newValue == true)
            toast(R.string.restart_module)
            true
        }
    }
}
