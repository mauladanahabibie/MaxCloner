package top.niunaijun.blackboxa.data

import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import android.webkit.URLUtil
import androidx.lifecycle.MutableLiveData
import java.io.File
import java.util.Locale
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.utils.AbiUtils
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.app.App
import top.niunaijun.blackboxa.app.AppManager
import top.niunaijun.blackboxa.bean.AppInfo
import top.niunaijun.blackboxa.bean.InstallOutcome
import top.niunaijun.blackboxa.bean.InstalledAppBean
import top.niunaijun.blackboxa.util.MemoryManager
import top.niunaijun.blackboxa.util.RecentClonesStore
import top.niunaijun.blackboxa.util.ShortcutUtil
import top.niunaijun.blackboxa.util.getString

class AppsRepository {
    val TAG: String = "AppsRepository"

    companion object {
        const val ALL_USERS_ID = -1

        fun resolveNextFreeUserId(
            installedUserIdsForPkg: Set<Int>,
            preferredUserId: Int,
            maxExistingUserId: Int
        ): Int {
            if (preferredUserId >= 0 && preferredUserId !in installedUserIdsForPkg) {
                return preferredUserId
            }
            var candidate = 0
            while (candidate in installedUserIdsForPkg) {
                candidate++
            }
            return candidate.coerceAtLeast(0)
        }
    }

    private data class CachedDeviceApp(
        val appInfo: AppInfo,
        val isSystem: Boolean
    )

    private val mDeviceApps = mutableListOf<CachedDeviceApp>()

    private fun safeLoadAppLabel(applicationInfo: ApplicationInfo): String {
        return try {
            BlackBoxCore.getPackageManager().getApplicationLabel(applicationInfo).toString()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load label for ${applicationInfo.packageName}: ${e.message}")
            applicationInfo.packageName
        }
    }

    private fun safeLoadAppIcon(applicationInfo: ApplicationInfo): Drawable? {
        return try {
            if (MemoryManager.shouldSkipIconLoading()) {
                Log.w(
                    TAG,
                    "Memory usage critical (${MemoryManager.getMemoryUsagePercentage()}%), skipping icon for ${applicationInfo.packageName}"
                )
                return null
            }

            val icon = BlackBoxCore.getPackageManager().getApplicationIcon(applicationInfo)
            if (icon is BitmapDrawable) {
                val bitmap = icon.bitmap
                if (bitmap.width > 144 || bitmap.height > 144) {
                    try {
                        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 144, 144, true)
                        BitmapDrawable(
                            BlackBoxCore.getPackageManager()
                                .getResourcesForApplication(applicationInfo.packageName),
                            scaledBitmap
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to scale icon for ${applicationInfo.packageName}: ${e.message}")
                        icon
                    }
                } else {
                    icon
                }
            } else {
                icon
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load icon for ${applicationInfo.packageName}: ${e.message}")
            null
        }
    }

    fun previewInstallList() {
        try {
            synchronized(mDeviceApps) {
                val installedApplications: List<ApplicationInfo> =
                    BlackBoxCore.getPackageManager().getInstalledApplications(0)
                val cached = mutableListOf<CachedDeviceApp>()
                val hostPkg = runCatching { BlackBoxCore.getHostPkg() }.getOrNull()

                for (installedApplication in installedApplications) {
                    try {
                        val sourceDir = installedApplication.sourceDir ?: continue
                        val file = File(sourceDir)
                        if (!file.exists()) continue

                        if (!AbiUtils.isSupport(file)) continue

                        val pkg = installedApplication.packageName ?: continue
                        if (pkg == hostPkg || BlackBoxCore.get().isBlackBoxApp(pkg)) {
                            continue
                        }

                        val isPureSystem =
                            (installedApplication.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                                (installedApplication.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0

                        val info = AppInfo(
                            safeLoadAppLabel(installedApplication),
                            safeLoadAppIcon(installedApplication),
                            pkg,
                            sourceDir,
                            false
                        )
                        cached.add(CachedDeviceApp(info, isPureSystem))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing app ${installedApplication.packageName}: ${e.message}")
                    }
                }
                mDeviceApps.clear()
                mDeviceApps.addAll(cached)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in previewInstallList: ${e.message}")
        }
    }

    fun getInstalledAppList(
        userID: Int,
        includeSystemApps: Boolean,
        loadingLiveData: MutableLiveData<Boolean>,
        appsLiveData: MutableLiveData<List<InstalledAppBean>>
    ) {
        try {
            loadingLiveData.postValue(true)
            synchronized(mDeviceApps) {
                if (mDeviceApps.isEmpty()) {
                    previewInstallList()
                }
                val blackBoxCore = BlackBoxCore.get()
                val users = runCatching { blackBoxCore.users }.getOrDefault(emptyList())

                val newInstalledList = mDeviceApps
                    .asSequence()
                    .filter { includeSystemApps || !it.isSystem }
                    .map { cached ->
                        val app = cached.appInfo
                        val cloneCount = users.count { user ->
                            runCatching { blackBoxCore.isInstalled(app.packageName, user.id) }
                                .getOrDefault(false)
                        }
                        InstalledAppBean(
                            name = app.name,
                            icon = app.icon,
                            packageName = app.packageName,
                            sourceDir = app.sourceDir,
                            isInstall = cloneCount > 0,
                            cloneCount = cloneCount
                        )
                    }
                    .sortedBy { it.name.lowercase(Locale.getDefault()) }
                    .toList()

                appsLiveData.postValue(newInstalledList)
                loadingLiveData.postValue(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in getInstalledAppList: ${e.message}")
            loadingLiveData.postValue(false)
            appsLiveData.postValue(emptyList())
        }
    }

    private fun buildPackageUserIndexMap(blackBoxCore: BlackBoxCore): Map<String, List<Int>> {
        val map = mutableMapOf<String, MutableList<Int>>()
        val users = runCatching { blackBoxCore.users.sortedBy { it.id } }.getOrDefault(emptyList())
        for (user in users) {
            val apps = runCatching { blackBoxCore.getInstalledApplications(0, user.id) }
                .getOrNull().orEmpty()
            for (app in apps) {
                val pkg = app.packageName ?: continue
                map.getOrPut(pkg) { mutableListOf() }.add(user.id)
            }
        }
        return map
    }

    fun getAllVmInstallList(
        appsLiveData: MutableLiveData<List<AppInfo>>,
        recentLiveData: MutableLiveData<List<AppInfo>>? = null
    ) {
        try {
            if (MemoryManager.isMemoryCritical()) {
                MemoryManager.forceGarbageCollectionIfNeeded()
            }

            val blackBoxCore = BlackBoxCore.get()
            val users = runCatching { blackBoxCore.users.sortedBy { it.id } }.getOrDefault(emptyList())
            val pkgUserMap = buildPackageUserIndexMap(blackBoxCore)

            val rawItems = mutableListOf<AppInfo>()
            val lookupMap = mutableMapOf<Pair<Int, String>, AppInfo>()

            for (user in users) {
                val apps = fetchAppsForUserWithRetry(blackBoxCore, user.id) ?: continue
                for (applicationInfo in apps) {
                    val pkg = applicationInfo.packageName
                    if (pkg.isNullOrBlank()) continue
                    val userIds = pkgUserMap[pkg].orEmpty()
                    val totalClones = userIds.size.coerceAtLeast(1)
                    val cloneIndex = (userIds.indexOf(user.id).takeIf { it >= 0 } ?: 0) + 1

                    val info = AppInfo(
                        name = safeLoadAppLabel(applicationInfo),
                        icon = safeLoadAppIcon(applicationInfo),
                        packageName = pkg,
                        sourceDir = applicationInfo.sourceDir ?: "",
                        isXpModule = false,
                        userId = user.id,
                        cloneIndex = cloneIndex,
                        totalClones = totalClones
                    )
                    rawItems.add(info)
                    lookupMap[user.id to pkg] = info
                }
            }

            val groupedSorted = rawItems.sortedWith(
                compareBy<AppInfo> { it.name.lowercase(Locale.getDefault()) }
                    .thenBy { it.cloneIndex }
                    .thenBy { it.userId }
            )
            appsLiveData.postValue(groupedSorted)

            if (recentLiveData != null) {
                val savedRecent = RecentClonesStore.getRecentEntries(App.getContext())
                    .mapNotNull { key -> lookupMap[key] }
                    .take(8)
                val resolvedRecent = if (savedRecent.isNotEmpty()) {
                    savedRecent
                } else {
                    rawItems.sortedByDescending { it.userId }.take(8)
                }
                recentLiveData.postValue(resolvedRecent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in getAllVmInstallList: ${e.message}")
            appsLiveData.postValue(emptyList())
            recentLiveData?.postValue(emptyList())
        }
    }

    fun getVmInstallList(
        userId: Int,
        appsLiveData: MutableLiveData<List<AppInfo>>,
        recentLiveData: MutableLiveData<List<AppInfo>>? = null
    ) {
        if (userId == ALL_USERS_ID) {
            getAllVmInstallList(appsLiveData, recentLiveData)
            return
        }

        try {
            if (MemoryManager.isMemoryCritical()) {
                MemoryManager.forceGarbageCollectionIfNeeded()
            }

            val blackBoxCore = BlackBoxCore.get()
            val sortListData = AppManager.mRemarkSharedPreferences.getString("AppList$userId", "")
            val sortList = sortListData?.split(",")?.filter { it.isNotBlank() }

            val applicationList = fetchAppsForUserWithRetry(blackBoxCore, userId)
            if (applicationList == null) {
                appsLiveData.postValue(emptyList())
                recentLiveData?.postValue(emptyList())
                return
            }

            val pkgUserMap = buildPackageUserIndexMap(blackBoxCore)

            val sortedApplicationList = if (!sortList.isNullOrEmpty()) {
                try {
                    applicationList.sortedWith(AppsSortComparator(sortList))
                } catch (e: Exception) {
                    Log.e(TAG, "getVmInstallList sort error: ${e.message}")
                    applicationList
                }
            } else {
                applicationList
            }

            val appInfoList = mutableListOf<AppInfo>()
            sortedApplicationList.forEachIndexed { index, applicationInfo ->
                try {
                    val pkg = applicationInfo.packageName
                    if (pkg.isNullOrBlank()) return@forEachIndexed
                    if (index > 0 && index % 25 == 0 && MemoryManager.isMemoryCritical()) {
                        MemoryManager.forceGarbageCollectionIfNeeded()
                    }
                    val userIds = pkgUserMap[pkg].orEmpty()
                    val totalClones = userIds.size.coerceAtLeast(1)
                    val cloneIndex = (userIds.indexOf(userId).takeIf { it >= 0 } ?: 0) + 1

                    appInfoList.add(
                        AppInfo(
                            name = safeLoadAppLabel(applicationInfo),
                            icon = safeLoadAppIcon(applicationInfo),
                            packageName = pkg,
                            sourceDir = applicationInfo.sourceDir ?: "",
                            isXpModule = false,
                            userId = userId,
                            cloneIndex = cloneIndex,
                            totalClones = totalClones
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing cloned app at $index: ${e.message}")
                }
            }

            appsLiveData.postValue(appInfoList)
            recentLiveData?.postValue(emptyList())
        } catch (e: Exception) {
            Log.e(TAG, "Error in getVmInstallList: ${e.message}")
            appsLiveData.postValue(emptyList())
            recentLiveData?.postValue(emptyList())
        }
    }

    private fun fetchAppsForUserWithRetry(
        blackBoxCore: BlackBoxCore,
        userId: Int
    ): List<ApplicationInfo>? {
        var applicationList: List<ApplicationInfo>? = null
        var retryCount = 0
        val maxRetries = 3
        while (applicationList == null && retryCount < maxRetries) {
            try {
                applicationList = blackBoxCore.getInstalledApplications(0, userId)
                if (applicationList == null) {
                    retryCount++
                    Thread.sleep(100)
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchAppsForUserWithRetry($userId) attempt ${retryCount + 1}: ${e.message}")
                retryCount++
                if (retryCount < maxRetries) {
                    Thread.sleep(200)
                }
            }
        }
        return applicationList
    }

    fun installApk(
        source: String,
        requestedUserId: Int,
        outcomeLiveData: MutableLiveData<InstallOutcome?>
    ) {
        try {
            val isUriSource = URLUtil.isContentUrl(source) || URLUtil.isValidUrl(source)
            val blackBoxCore = BlackBoxCore.get()

            if (!isUriSource &&
                (source.contains("blackbox") ||
                    source.contains("niunaijun") ||
                    source.contains("vspace") ||
                    source.contains("virtual"))
            ) {
                try {
                    val hostPackageName = BlackBoxCore.getHostPkg()
                    val file = File(source)
                    if (file.exists()) {
                        val packageInfo = BlackBoxCore.getPackageManager()
                            .getPackageArchiveInfo(source, 0)
                        if (packageInfo != null && packageInfo.packageName == hostPackageName) {
                            outcomeLiveData.postValue(
                                InstallOutcome(
                                    success = false,
                                    message = getString(R.string.install_fail, hostPackageName),
                                    userId = requestedUserId,
                                    isInstallAction = true
                                )
                            )
                            return
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not verify archive pkg: ${e.message}")
                }
            }

            val preKnownPkg: String? = when {
                isUriSource -> null
                !source.contains("/") -> source
                else -> runCatching {
                    val file = File(source)
                    if (file.exists()) {
                        BlackBoxCore.getPackageManager().getPackageArchiveInfo(source, 0)?.packageName
                    } else {
                        null
                    }
                }.getOrNull()
            }

            val existingUsers = runCatching { blackBoxCore.users }.getOrDefault(emptyList())
            val maxExistingUserId = existingUsers.maxOfOrNull { it.id } ?: -1

            val targetUserId = if (preKnownPkg != null) {
                val installedForPkg = existingUsers
                    .filter { runCatching { blackBoxCore.isInstalled(preKnownPkg, it.id) }.getOrDefault(false) }
                    .map { it.id }
                    .toSet()
                resolveNextFreeUserId(installedForPkg, requestedUserId, maxExistingUserId)
            } else {
                if (requestedUserId >= 0) requestedUserId else (maxExistingUserId + 1).coerceAtLeast(0)
            }

            val installResult = if (isUriSource) {
                blackBoxCore.installPackageAsUser(Uri.parse(source), targetUserId)
            } else {
                blackBoxCore.installPackageAsUser(source, targetUserId)
            }

            if (installResult.success) {
                updateAppSortList(targetUserId, installResult.packageName, true)
                RecentClonesStore.recordClone(App.getContext(), targetUserId, installResult.packageName)
                outcomeLiveData.postValue(
                    InstallOutcome(
                        success = true,
                        message = getString(R.string.install_success),
                        packageName = installResult.packageName,
                        userId = targetUserId,
                        isInstallAction = true
                    )
                )
            } else {
                val errorDetail = installResult.msg ?: getString(R.string.install_fail_no_msg)
                outcomeLiveData.postValue(
                    InstallOutcome(
                        success = false,
                        message = getString(R.string.install_fail, errorDetail),
                        userId = targetUserId,
                        isInstallAction = true
                    )
                )
            }
            scanUser()
        } catch (e: Exception) {
            Log.e(TAG, "Error installing APK: ${e.message}")
            outcomeLiveData.postValue(
                InstallOutcome(
                    success = false,
                    message = getString(R.string.install_fail, e.message ?: "unknown"),
                    userId = requestedUserId.coerceAtLeast(0),
                    isInstallAction = true
                )
            )
        }
    }

    fun unInstall(
        packageName: String,
        userID: Int,
        outcomeLiveData: MutableLiveData<InstallOutcome?>
    ) {
        try {
            BlackBoxCore.get().uninstallPackageAsUser(packageName, userID)
            ShortcutUtil.disableShortcut(App.getContext(), userID, packageName)
            RecentClonesStore.removeClone(App.getContext(), userID, packageName)
            updateAppSortList(userID, packageName, false)
            scanUser()
            outcomeLiveData.postValue(
                InstallOutcome(
                    success = true,
                    message = getString(R.string.uninstall_success),
                    packageName = packageName,
                    userId = userID,
                    isInstallAction = false
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error uninstalling APK: ${e.message}")
            outcomeLiveData.postValue(
                InstallOutcome(
                    success = false,
                    message = getString(R.string.uninstall_fail),
                    packageName = packageName,
                    userId = userID,
                    isInstallAction = false
                )
            )
        }
    }

    fun launchApk(packageName: String, userId: Int, launchLiveData: MutableLiveData<Boolean?>) {
        try {
            val result = BlackBoxCore.get().launchApk(packageName, userId)
            launchLiveData.postValue(result)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching APK: ${e.message}")
            launchLiveData.postValue(false)
        }
    }

    fun clearApkData(
        packageName: String,
        userID: Int,
        outcomeLiveData: MutableLiveData<InstallOutcome?>
    ) {
        try {
            BlackBoxCore.get().clearPackage(packageName, userID)
            outcomeLiveData.postValue(
                InstallOutcome(
                    success = true,
                    message = getString(R.string.clear_success),
                    packageName = packageName,
                    userId = userID,
                    isInstallAction = false
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing APK data: ${e.message}")
            outcomeLiveData.postValue(
                InstallOutcome(
                    success = false,
                    message = "Clear failed: ${e.message}",
                    packageName = packageName,
                    userId = userID,
                    isInstallAction = false
                )
            )
        }
    }

    private fun scanUser() {
        try {
            val blackBoxCore = BlackBoxCore.get()
            val userList = blackBoxCore.users
            if (userList.isEmpty()) return

            val id = userList.last().id
            if (blackBoxCore.getInstalledApplications(0, id).isEmpty()) {
                blackBoxCore.deleteUser(id)
                AppManager.mRemarkSharedPreferences.edit().apply {
                    remove("Remark$id")
                    remove("AppList$id")
                    apply()
                }
                scanUser()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in scanUser: ${e.message}")
        }
    }

    private fun updateAppSortList(userID: Int, pkg: String, isAdd: Boolean) {
        try {
            val savedSortList = AppManager.mRemarkSharedPreferences.getString("AppList$userID", "")
            val sortList = linkedSetOf<String>()
            if (!savedSortList.isNullOrBlank()) {
                sortList.addAll(savedSortList.split(",").filter { it.isNotBlank() })
            }

            if (isAdd) {
                sortList.add(pkg)
            } else {
                sortList.remove(pkg)
            }

            AppManager.mRemarkSharedPreferences.edit().apply {
                putString("AppList$userID", sortList.joinToString(","))
                apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating app sort list: ${e.message}")
        }
    }

    fun updateApkOrder(userID: Int, dataList: List<AppInfo>) {
        try {
            AppManager.mRemarkSharedPreferences.edit().apply {
                putString("AppList$userID", dataList.joinToString(",") { it.packageName })
                apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating APK order: ${e.message}")
        }
    }
}
