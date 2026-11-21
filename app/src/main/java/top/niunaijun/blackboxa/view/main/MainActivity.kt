package top.niunaijun.blackboxa.view.main

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.drawable.DrawableCompat
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.app.AppManager
import top.niunaijun.blackboxa.data.AppsRepository
import top.niunaijun.blackboxa.databinding.ActivityMainBinding
import top.niunaijun.blackboxa.databinding.DialogRenameSpaceBinding
import top.niunaijun.blackboxa.util.inflate
import top.niunaijun.blackboxa.view.apps.AppsFragment
import top.niunaijun.blackboxa.view.base.LoadingActivity
import top.niunaijun.blackboxa.view.fake.FakeManagerActivity
import top.niunaijun.blackboxa.view.gms.GmsManagerActivity
import top.niunaijun.blackboxa.view.list.ListActivity
import top.niunaijun.blackboxa.view.setting.SettingActivity

class MainActivity : LoadingActivity() {

    private val viewBinding: ActivityMainBinding by inflate()

    private lateinit var mViewPagerAdapter: ViewPagerAdapter
    private val fragmentList = mutableListOf<AppsFragment>()
    private var currentUser = AppsFragment.ALL_USERS_ID

    companion object {
        private const val TAG = "MainActivity"
        private const val STORAGE_PERMISSION_REQUEST_CODE = 1001

        fun start(context: Context) {
            context.startActivity(Intent(context, MainActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)

            runCatching { BlackBoxCore.get().onBeforeMainActivityOnCreate(this) }

            setContentView(viewBinding.root)
            setupEdgeToEdge(
                topView = viewBinding.headerContainer,
                bottomView = viewBinding.fab,
                applyBottomAsMargin = true
            )

            initToolbar(viewBinding.toolbarLayout.toolbar, R.string.app_name)
            viewBinding.toolbarLayout.toolbar.setContentInsetsRelative(12, 16)

            initViewPager()
            initFab()
            initSearchBar()
            initSpaceControls()
            setupBackHandler()

            checkStoragePermission()
            if (AppManager.mBlackBoxLoader.useVpnNetwork()) {
                checkVpnPermission()
            }

            runCatching { BlackBoxCore.get().onAfterMainActivityOnCreate(this) }
        } catch (e: Exception) {
            Log.e(TAG, "Critical error in onCreate: ${e.message}")
            showErrorDialog("Failed to initialize app: ${e.message}")
        }
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (viewBinding.mainSearchContainer.visibility == View.VISIBLE) {
                        closeSearchBar()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )
    }

    private fun initSearchBar() {
        viewBinding.etMainSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString().orEmpty()
                viewBinding.btnClearMainSearch.visibility =
                    if (query.isEmpty()) View.GONE else View.VISIBLE
                fragmentList.firstOrNull { it.userID == currentUser }?.setSearchQuery(query)
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        viewBinding.btnClearMainSearch.setOnClickListener {
            viewBinding.etMainSearch.setText("")
        }
    }

    private fun toggleSearchBar() {
        if (viewBinding.mainSearchContainer.visibility == View.VISIBLE) {
            closeSearchBar()
        } else {
            viewBinding.mainSearchContainer.visibility = View.VISIBLE
            viewBinding.etMainSearch.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(viewBinding.etMainSearch, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun closeSearchBar() {
        viewBinding.etMainSearch.setText("")
        viewBinding.mainSearchContainer.visibility = View.GONE
        fragmentList.firstOrNull { it.userID == currentUser }?.setSearchQuery("")
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        window.peekDecorView()?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    private fun initSpaceControls() {
        updateSpaceHeaderState()
        rebuildSpaceChips()

        viewBinding.btnRenameSpace.setOnClickListener {
            if (currentUser >= 0) {
                showRenameSpaceDialog(currentUser)
            }
        }
    }

    private fun showRenameSpaceDialog(userId: Int) {
        if (userId < 0) return
        val dialogBinding = DialogRenameSpaceBinding.inflate(layoutInflater)
        val currentName = AppManager.getSpaceDisplayName(userId)
        dialogBinding.etSpaceName.setText(currentName)
        dialogBinding.etSpaceName.setSelection(currentName.length)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rename_space)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.done) { _, _ ->
                val newName = dialogBinding.etSpaceName.text?.toString()?.trim().orEmpty()
                AppManager.mRemarkSharedPreferences.edit {
                    if (newName.isEmpty()) {
                        remove("Remark$userId")
                    } else {
                        putString("Remark$userId", newName)
                    }
                }
                updateSpaceHeaderState()
                rebuildSpaceChips()
                refreshAllFragments()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun refreshSpaceChips() {
        runOnUiThread {
            rebuildSpaceChips()
        }
    }

    fun refreshAllFragments() {
        runOnUiThread {
            supportFragmentManager.fragments
                .filterIsInstance<AppsFragment>()
                .forEach { it.refreshApps() }
            rebuildSpaceChips()
        }
    }

    fun switchToAllTabAndRefresh() {
        runOnUiThread {
            scanUser()
            if (fragmentList.isNotEmpty()) {
                viewBinding.viewPager.setCurrentItem(0, true)
            }
            refreshAllFragments()
        }
    }

    private fun rebuildSpaceChips() {
        if (isFinishing || isDestroyed) return
        val users = runCatching { BlackBoxCore.get().users }.getOrDefault(emptyList())
        val chipGroup = viewBinding.spaceChipGroup
        chipGroup.removeAllViews()

        fragmentList.forEachIndexed { index, fragment ->
            val userId = fragment.userID
            val label = if (userId == AppsFragment.ALL_USERS_ID) {
                val allLabel = getString(R.string.all_clones_tab)
                val cachedCount = fragment.getAppCount()
                val totalCount = if (cachedCount > 0) {
                    cachedCount
                } else {
                    users.sumOf { user ->
                        runCatching {
                            BlackBoxCore.get().getInstalledApplications(0, user.id).size
                        }.getOrDefault(0)
                    }
                }
                if (totalCount > 0) {
                    getString(R.string.space_chip_with_count, allLabel, totalCount)
                } else {
                    allLabel
                }
            } else {
                val spaceName = AppManager.getSpaceDisplayName(userId)
                val cachedCount = fragment.getAppCount()
                val liveCount = if (cachedCount > 0) {
                    cachedCount
                } else {
                    runCatching { BlackBoxCore.get().getInstalledApplications(0, userId).size }
                        .getOrDefault(0)
                }
                if (liveCount > 0) {
                    getString(R.string.space_chip_with_count, spaceName, liveCount)
                } else {
                    spaceName
                }
            }

            val chip = Chip(this, null, com.google.android.material.R.attr.chipStyle).apply {
                id = View.generateViewId()
                text = label
                isCheckable = true
                isChecked = (userId == currentUser)
                chipBackgroundColor = ContextCompat.getColorStateList(
                    this@MainActivity,
                    R.color.bg_space_chip_selector
                )
                chipStrokeColor = ContextCompat.getColorStateList(
                    this@MainActivity,
                    R.color.stroke_space_chip_selector
                )
                chipStrokeWidth = resources.displayMetrics.density
                setTextColor(
                    ContextCompat.getColorStateList(
                        this@MainActivity,
                        R.color.text_space_chip_selector
                    )
                )
                isCheckedIconVisible = false
                chipCornerRadius = 20f * resources.displayMetrics.density
                setOnClickListener {
                    if (viewBinding.viewPager.currentItem != index) {
                        viewBinding.viewPager.setCurrentItem(index, true)
                    } else {
                        isChecked = true
                    }
                }
            }
            chipGroup.addView(chip)
        }
    }

    private fun initViewPager() {
        val userList = runCatching { BlackBoxCore.get().users }.getOrDefault(emptyList())
        fragmentList.clear()
        fragmentList.add(AppsFragment.newInstance(AppsFragment.ALL_USERS_ID))
        userList.sortedBy { it.id }.distinctBy { it.id }.forEach {
            fragmentList.add(AppsFragment.newInstance(it.id))
        }

        currentUser = AppsFragment.ALL_USERS_ID

        mViewPagerAdapter = ViewPagerAdapter(this)
        mViewPagerAdapter.replaceData(fragmentList)
        viewBinding.viewPager.adapter = mViewPagerAdapter
        viewBinding.viewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    if (position in fragmentList.indices) {
                        currentUser = fragmentList[position].userID
                        updateSpaceHeaderState()
                        rebuildSpaceChips()
                        invalidateOptionsMenu()
                        showFloatButton(true)
                        val query = viewBinding.etMainSearch.text?.toString().orEmpty()
                        fragmentList[position].setSearchQuery(query)
                    }
                }
            }
        )
    }

    private fun initFab() {
        viewBinding.fab.setOnClickListener {
            openAppChooserForCurrentSpace()
        }
    }

    fun openAppChooserForCurrentSpace() {
        try {
            val pos = viewBinding.viewPager.currentItem
            val targetUserId = fragmentList.getOrNull(pos)?.userID ?: currentUser
            val intent = Intent(this, ListActivity::class.java).apply {
                putExtra("userID", targetUserId)
            }
            apkPathResult.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching ListActivity: ${e.message}")
        }
    }

    fun getNextCloneTargetUserId(packageName: String): Int {
        val users = runCatching { BlackBoxCore.get().users }.getOrDefault(emptyList())
        val installedUserIds = users
            .filter { runCatching { BlackBoxCore.get().isInstalled(packageName, it.id) }.getOrDefault(false) }
            .map { it.id }
            .toSet()
        val maxId = users.maxOfOrNull { it.id } ?: -1
        return AppsRepository.resolveNextFreeUserId(
            installedUserIdsForPkg = installedUserIds,
            preferredUserId = if (currentUser >= 0) currentUser else AppsFragment.ALL_USERS_ID,
            maxExistingUserId = maxId
        )
    }

    fun clonePackageToNextSpace(packageName: String) {
        val pos = viewBinding.viewPager.currentItem.coerceIn(0, fragmentList.lastIndex)
        fragmentList.getOrNull(pos)?.installApk(packageName)
    }

    fun showFloatButton(show: Boolean) {
        if (show) {
            viewBinding.fab.extend()
        } else {
            viewBinding.fab.shrink()
        }
    }

    fun scanUser() {
        try {
            val userList = BlackBoxCore.get().users.sortedBy { it.id }
            val expectedIds = listOf(AppsFragment.ALL_USERS_ID) + userList.map { it.id }.distinct()

            val existingIds = fragmentList.map { it.userID }.toSet()
            fragmentList.removeAll { it.userID !in expectedIds }
            expectedIds.filter { it !in existingIds }.forEach { missingId ->
                fragmentList.add(AppsFragment.newInstance(missingId))
            }

            val uniqueFragments = fragmentList.distinctBy { it.userID }
            if (uniqueFragments.size != fragmentList.size) {
                fragmentList.clear()
                fragmentList.addAll(uniqueFragments)
            }
            fragmentList.sortBy { it.userID }

            if (fragmentList.isEmpty()) {
                fragmentList.add(AppsFragment.newInstance(AppsFragment.ALL_USERS_ID))
            }

            mViewPagerAdapter.notifyDataSetChanged()
            val pos = viewBinding.viewPager.currentItem.coerceIn(0, fragmentList.lastIndex)
            currentUser = fragmentList[pos].userID
            updateSpaceHeaderState()
            rebuildSpaceChips()
            invalidateOptionsMenu()
        } catch (e: Exception) {
            Log.e(TAG, "Error in scanUser: ${e.message}")
        }
    }

    private fun updateSpaceHeaderState() {
        viewBinding.toolbarLayout.toolbar.subtitle = null
        viewBinding.btnRenameSpace.visibility = if (currentUser >= 0) View.VISIBLE else View.GONE
    }

    private val apkPathResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            try {
                if (result.resultCode == RESULT_OK) {
                    result.data?.let { data ->
                        val userId = data.getIntExtra("userID", currentUser)
                        val source = data.getStringExtra("source")
                        if (!source.isNullOrBlank()) {
                            val targetFragment = fragmentList.firstOrNull { it.userID == userId }
                                ?: fragmentList.firstOrNull()
                            targetFragment?.installApk(source)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling APK path result: ${e.message}")
            }
        }

    private fun checkStoragePermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    showStoragePermissionDialog()
                }
            } else {
                if (ContextCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.READ_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestLegacyStoragePermission()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking storage permission: ${e.message}")
        }
    }

    private fun requestLegacyStoragePermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ),
            STORAGE_PERMISSION_REQUEST_CODE
        )
    }

    private fun showStoragePermissionDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.storage_permission_title)
            .setMessage(R.string.storage_permission_message)
            .setPositiveButton(R.string.grant_permission) { _, _ -> openAllFilesAccessSettings() }
            .setNegativeButton(R.string.later, null)
            .setCancelable(false)
            .show()
    }

    private fun openAllFilesAccessSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                storagePermissionResult.launch(intent)
            }
        } catch (e: Exception) {
            runCatching {
                storagePermissionResult.launch(
                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                )
            }
        }
    }

    private val storagePermissionResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    private fun checkVpnPermission() {
        try {
            val vpnIntent = VpnService.prepare(this)
            if (vpnIntent != null) {
                vpnPermissionResult.launch(vpnIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking VPN permission: ${e.message}")
        }
    }

    private val vpnPermissionResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    private fun showErrorDialog(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton(R.string.done) { _, _ -> finish() }
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val iconTint = ContextCompat.getColor(this, R.color.mc_text_primary)
        menu?.let {
            for (i in 0 until it.size()) {
                it.getItem(i).icon?.let { drawable ->
                    val wrapped = DrawableCompat.wrap(drawable.mutate())
                    DrawableCompat.setTint(wrapped, iconTint)
                    it.getItem(i).icon = wrapped
                }
            }
        }
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.findItem(R.id.rename_space)?.isVisible = (currentUser >= 0)
        menu?.findItem(R.id.stop_all_space)?.setTitle(
            if (currentUser == AppsFragment.ALL_USERS_ID) {
                R.string.stop_all_clones
            } else {
                R.string.stop_all_in_space
            }
        )
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.main_search -> {
                toggleSearchBar()
                true
            }
            R.id.main_setting -> {
                SettingActivity.start(this)
                true
            }
            R.id.fake_location -> {
                val targetSpace = if (currentUser >= 0) {
                    currentUser
                } else {
                    runCatching { BlackBoxCore.get().users.firstOrNull()?.id }.getOrNull() ?: 0
                }
                val intent = Intent(this, FakeManagerActivity::class.java).apply {
                    putExtra("userID", targetSpace)
                }
                startActivity(intent)
                true
            }
            R.id.main_gms -> {
                GmsManagerActivity.start(this)
                true
            }
            R.id.rename_space -> {
                if (currentUser >= 0) {
                    showRenameSpaceDialog(currentUser)
                }
                true
            }
            R.id.stop_all_space -> {
                stopAllAppsInCurrentSpace()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun stopAllAppsInCurrentSpace() {
        try {
            val targetUsers = if (currentUser == AppsFragment.ALL_USERS_ID) {
                BlackBoxCore.get().users.map { it.id }
            } else {
                listOf(currentUser)
            }
            for (uid in targetUsers) {
                val apps = BlackBoxCore.get().getInstalledApplications(0, uid)
                apps.forEach { app ->
                    runCatching { BlackBoxCore.get().stopPackage(app.packageName, uid) }
                }
            }
            val message = if (currentUser == AppsFragment.ALL_USERS_ID) {
                getString(R.string.stop_all_clones_done)
            } else {
                getString(R.string.stop_all_done, AppManager.getSpaceDisplayName(currentUser))
            }
            Snackbar.make(
                findViewById(android.R.id.content),
                message,
                Snackbar.LENGTH_SHORT
            )
                .setBackgroundTint(ContextCompat.getColor(this, R.color.mc_surface_card_elevated))
                .setTextColor(ContextCompat.getColor(this, R.color.mc_text_primary))
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping all apps: ${e.message}")
        }
    }
}
