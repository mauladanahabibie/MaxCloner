package top.niunaijun.blackboxa.view.apps

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cbfg.rvadapter.RVAdapter
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.app.AppManager
import top.niunaijun.blackboxa.bean.AppInfo
import top.niunaijun.blackboxa.databinding.FragmentAppsBinding
import top.niunaijun.blackboxa.databinding.SheetAppActionsBinding
import top.niunaijun.blackboxa.util.ContextUtil.openAppSystemSettings
import top.niunaijun.blackboxa.util.InjectionUtil
import top.niunaijun.blackboxa.util.ShortcutUtil
import top.niunaijun.blackboxa.util.inflate
import top.niunaijun.blackboxa.util.toast
import top.niunaijun.blackboxa.view.base.LoadingActivity
import top.niunaijun.blackboxa.view.main.MainActivity

class AppsFragment : Fragment() {

    val userID: Int
        get() = arguments?.getInt("userID", 0) ?: 0

    private lateinit var viewModel: AppsViewModel
    private lateinit var mAdapter: RVAdapter<AppInfo>
    private val viewBinding: FragmentAppsBinding by inflate()

    private var fullAppList: List<AppInfo> = emptyList()
    private var currentSearchQuery: String = ""
    private var pendingInstallSource: String? = null

    companion object {
        private const val TAG = "AppsFragment"

        fun newInstance(userID: Int): AppsFragment {
            return AppsFragment().apply {
                arguments = bundleOf("userID" to userID)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this, InjectionUtil.getAppsFactory())[AppsViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        mAdapter = RVAdapter<AppInfo>(
            requireContext(),
            AppsAdapter(userID) { app -> showAppActionsSheet(app) }
        ).bind(viewBinding.recyclerView)

        val layoutManager = GridLayoutManager(requireContext(), 3).apply {
            isItemPrefetchEnabled = true
            initialPrefetchItemCount = 9
        }
        viewBinding.recyclerView.layoutManager = layoutManager
        viewBinding.recyclerView.adapter = mAdapter
        viewBinding.recyclerView.setItemViewCacheSize(20)

        viewBinding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy > 12) {
                    (activity as? MainActivity)?.showFloatButton(false)
                } else if (dy < -12) {
                    (activity as? MainActivity)?.showFloatButton(true)
                }
            }
        })

        mAdapter.setItemClickListener { _, data, _ ->
            launchApp(data)
        }
        mAdapter.setItemLongClickListener { _, data, _ ->
            showAppActionsSheet(data)
        }

        viewBinding.btnCloneFirst.setOnClickListener {
            (activity as? MainActivity)?.openAppChooserForCurrentSpace()
        }

        return viewBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initData()
        pendingInstallSource?.let { source ->
            pendingInstallSource = null
            installApk(source)
        }
    }

    override fun onStart() {
        super.onStart()
        try {
            BlackBoxCore.get().addServiceAvailableCallback {
                if (isAdded) {
                    viewModel.getInstalledAppsWithRetry(userID)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error registering service available callback: ${e.message}")
        }
        viewModel.getInstalledAppsWithRetry(userID)
    }

    override fun onResume() {
        super.onResume()
        if (this::mAdapter.isInitialized) {
            mAdapter.notifyDataSetChanged()
        }
    }

    fun getAppCount(): Int = fullAppList.size

    fun setSearchQuery(query: String) {
        currentSearchQuery = query.trim()
        if (this::mAdapter.isInitialized && view != null) {
            applyFilterAndUpdateUi()
        }
    }

    private fun initData() {
        viewBinding.loadingIndicator.visibility = View.VISIBLE
        viewModel.getInstalledApps(userID)

        viewModel.appsLiveData.observe(viewLifecycleOwner) { apps ->
            viewBinding.loadingIndicator.visibility = View.GONE
            if (apps != null) {
                fullAppList = apps
                applyFilterAndUpdateUi()
                (activity as? MainActivity)?.refreshSpaceChips()
            }
        }

        viewModel.outcomeLiveData.observe(viewLifecycleOwner) { outcome ->
            if (outcome == null) return@observe
            viewModel.outcomeLiveData.value = null
            hideLoading()

            viewModel.getInstalledApps(userID)
            (activity as? MainActivity)?.scanUser()

            if (outcome.success && outcome.isInstallAction && outcome.packageName != null) {
                handleSuccessfulClone(outcome.packageName)
            } else {
                showStatusSnackbar(outcome.message)
            }
        }

        viewModel.launchLiveData.observe(viewLifecycleOwner) { success ->
            if (success == null) return@observe
            viewModel.launchLiveData.value = null
            hideLoading()
            if (!success) {
                toast(R.string.start_fail)
            }
        }
    }

    private fun handleSuccessfulClone(packageName: String) {
        val ctx = context ?: return
        val appInfo = ShortcutUtil.loadClonedAppInfo(ctx, packageName, userID)
        if (appInfo == null) {
            showStatusSnackbar(getString(R.string.install_success))
            return
        }

        if (AppManager.mBlackBoxLoader.autoPinShortcut()) {
            val result = ShortcutUtil.requestAutoPinShortcut(ctx, userID, appInfo)
            if (this::mAdapter.isInitialized) {
                mAdapter.notifyDataSetChanged()
            }
            when (result) {
                ShortcutUtil.PinResult.PERMISSION_REQUIRED,
                ShortcutUtil.PinResult.UNSUPPORTED -> {
                    ShortcutUtil.showShortcutPermissionDialog(ctx)
                }
                ShortcutUtil.PinResult.REQUESTED,
                ShortcutUtil.PinResult.UPDATED -> {
                    showStatusSnackbar(
                        message = getString(R.string.cloned_ready_msg, appInfo.name),
                        actionLabel = getString(R.string.open_now)
                    ) {
                        launchApp(appInfo)
                    }
                }
            }
        } else {
            showStatusSnackbar(
                message = getString(R.string.cloned_ready_msg, appInfo.name),
                actionLabel = getString(R.string.add_to_home_screen)
            ) {
                when (ShortcutUtil.requestAutoPinShortcut(ctx, userID, appInfo)) {
                    ShortcutUtil.PinResult.PERMISSION_REQUIRED,
                    ShortcutUtil.PinResult.UNSUPPORTED -> ShortcutUtil.showShortcutPermissionDialog(ctx)
                    ShortcutUtil.PinResult.REQUESTED -> toast(getString(R.string.shortcut_sent_toast, appInfo.name))
                    ShortcutUtil.PinResult.UPDATED -> toast(getString(R.string.shortcut_updated_toast, appInfo.name))
                }
            }
        }
    }

    private fun applyFilterAndUpdateUi() {
        val filtered = if (currentSearchQuery.isBlank()) {
            fullAppList
        } else {
            fullAppList.filter {
                it.name.contains(currentSearchQuery, ignoreCase = true) ||
                    it.packageName.contains(currentSearchQuery, ignoreCase = true)
            }
        }

        mAdapter.setItems(filtered)

        when {
            fullAppList.isEmpty() -> {
                viewBinding.recyclerView.visibility = View.GONE
                viewBinding.emptySearchContainer.visibility = View.GONE
                viewBinding.emptyStateContainer.visibility = View.VISIBLE
                viewBinding.tvEmptyTitle.text = getString(
                    R.string.empty_space_title,
                    AppManager.getSpaceDisplayName(userID)
                )
            }
            filtered.isEmpty() -> {
                viewBinding.recyclerView.visibility = View.GONE
                viewBinding.emptyStateContainer.visibility = View.GONE
                viewBinding.emptySearchContainer.visibility = View.VISIBLE
            }
            else -> {
                viewBinding.emptyStateContainer.visibility = View.GONE
                viewBinding.emptySearchContainer.visibility = View.GONE
                viewBinding.recyclerView.visibility = View.VISIBLE
            }
        }
    }

    private fun showAppActionsSheet(info: AppInfo) {
        val ctx = context ?: return
        val dialog = BottomSheetDialog(ctx)
        val sheetBinding = SheetAppActionsBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        sheetBinding.ivSheetIcon.setImageDrawable(
            info.icon ?: ContextCompat.getDrawable(ctx, R.drawable.ic_brand_logo)
        )
        sheetBinding.tvSheetAppName.text = info.name
        sheetBinding.tvSheetPackage.text = info.packageName
        sheetBinding.tvSheetSpace.text = AppManager.getSpaceDisplayName(userID)

        val isPinned = ShortcutUtil.isShortcutPinned(ctx, userID, info.packageName)
        if (isPinned) {
            sheetBinding.tvSheetPinStatus.text = getString(R.string.shortcut_already_on_home)
            sheetBinding.tvSheetPinStatus.setBackgroundResource(R.drawable.bg_pill_success)
            sheetBinding.tvSheetPinStatus.setTextColor(
                ContextCompat.getColor(ctx, R.color.mc_on_success_container)
            )
            sheetBinding.tvAddHomeTitle.text = getString(R.string.add_another_home_icon)
        } else {
            sheetBinding.tvSheetPinStatus.text = getString(R.string.shortcut_not_on_home)
            sheetBinding.tvSheetPinStatus.setBackgroundResource(R.drawable.bg_pill_muted)
            sheetBinding.tvSheetPinStatus.setTextColor(
                ContextCompat.getColor(ctx, R.color.mc_text_secondary)
            )
            sheetBinding.tvAddHomeTitle.text = getString(R.string.add_to_home_screen)
        }

        val nextUserId = (activity as? MainActivity)?.getNextCloneTargetUserId(info.packageName)
            ?: (userID + 1)
        sheetBinding.tvCloneNextSubtitle.text = getString(
            R.string.clone_to_next_space_subtitle,
            AppManager.getSpaceDisplayName(nextUserId)
        )

        sheetBinding.actionAddHome.setOnClickListener {
            dialog.dismiss()
            ShortcutUtil.showCustomizeShortcutDialog(ctx, userID, info) { result ->
                when (result) {
                    ShortcutUtil.PinResult.PERMISSION_REQUIRED,
                    ShortcutUtil.PinResult.UNSUPPORTED -> ShortcutUtil.showShortcutPermissionDialog(ctx)
                    ShortcutUtil.PinResult.REQUESTED -> {
                        showStatusSnackbar(getString(R.string.shortcut_sent_toast, info.name))
                    }
                    ShortcutUtil.PinResult.UPDATED -> {
                        showStatusSnackbar(getString(R.string.shortcut_updated_toast, info.name))
                    }
                }
                if (this::mAdapter.isInitialized) {
                    mAdapter.notifyDataSetChanged()
                }
            }
        }

        sheetBinding.actionOpen.setOnClickListener {
            dialog.dismiss()
            launchApp(info)
        }

        sheetBinding.actionCloneNextSpace.setOnClickListener {
            dialog.dismiss()
            (activity as? MainActivity)?.clonePackageToNextSpace(info.packageName)
        }

        sheetBinding.actionStop.setOnClickListener {
            dialog.dismiss()
            stopApk(info)
        }

        sheetBinding.actionClear.setOnClickListener {
            dialog.dismiss()
            clearApk(info)
        }

        sheetBinding.actionRemove.setOnClickListener {
            dialog.dismiss()
            if (info.isXpModule) {
                toast(R.string.uninstall_module_toast)
            } else {
                unInstallApk(info)
            }
        }

        dialog.show()
    }

    private fun launchApp(info: AppInfo) {
        try {
            showLoading(getString(R.string.opening_app_progress, info.name))
            viewModel.launchApk(info.packageName, userID)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching app: ${e.message}")
            hideLoading()
        }
    }

    private fun unInstallApk(info: AppInfo) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.uninstall_app)
            .setMessage(getString(R.string.uninstall_app_hint, info.name))
            .setPositiveButton(R.string.app_remove) { _, _ ->
                showLoading(getString(R.string.removing_clone_progress))
                viewModel.unInstall(info.packageName, userID)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun stopApk(info: AppInfo) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.app_stop)
            .setMessage(getString(R.string.app_stop_hint, info.name))
            .setPositiveButton(R.string.app_stop) { _, _ ->
                try {
                    BlackBoxCore.get().stopPackage(info.packageName, userID)
                    showStatusSnackbar(getString(R.string.is_stop, info.name))
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping app: ${e.message}")
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun clearApk(info: AppInfo) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.app_clear)
            .setMessage(getString(R.string.app_clear_hint, info.name))
            .setPositiveButton(R.string.app_clear) { _, _ ->
                showLoading(getString(R.string.clearing_data_progress))
                viewModel.clearApkData(info.packageName, userID)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun installApk(source: String) {
        if (!this::viewModel.isInitialized) {
            pendingInstallSource = source
            return
        }
        try {
            showLoading(getString(R.string.cloning_app_progress))
            viewModel.install(source, userID)
        } catch (e: Exception) {
            Log.e(TAG, "Error installing APK: ${e.message}")
            hideLoading()
        }
    }

    private fun showStatusSnackbar(
        message: String,
        actionLabel: String? = null,
        action: (() -> Unit)? = null
    ) {
        val anchorHost = activity?.findViewById<View>(android.R.id.content) ?: return
        val ctx = context ?: return
        val snackbar = Snackbar.make(anchorHost, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(ContextCompat.getColor(ctx, R.color.mc_surface_card_elevated))
            .setTextColor(ContextCompat.getColor(ctx, R.color.mc_text_primary))
        if (actionLabel != null && action != null) {
            snackbar.setActionTextColor(ContextCompat.getColor(ctx, R.color.mc_primary))
            snackbar.setAction(actionLabel) { action() }
        }
        snackbar.show()
    }

    private fun showLoading(message: String? = null) {
        (activity as? LoadingActivity)?.showLoading(message)
    }

    private fun hideLoading() {
        (activity as? LoadingActivity)?.hideLoading()
    }
}
