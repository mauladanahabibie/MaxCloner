package top.niunaijun.blackboxa.data

import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.entity.location.BLocation
import top.niunaijun.blackbox.fake.frameworks.BLocationManager
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.app.App
import top.niunaijun.blackboxa.bean.FakeLocationBean

class FakeLocationRepository {
    val TAG: String = "FakeLocationRepository"

    fun setPattern(userId: Int, pkg: String, pattern: Int) {
        try {
            BLocationManager.get().setPattern(userId, pkg, pattern)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting pattern for $pkg: ${e.message}")
        }
    }

    private fun getPattern(userId: Int, pkg: String): Int {
        return try {
            BLocationManager.get().getPattern(userId, pkg)
        } catch (e: Exception) {
            Log.w(TAG, "Error reading pattern for $pkg: ${e.message}")
            BLocationManager.CLOSE_MODE
        }
    }

    private fun getLocation(userId: Int, pkg: String): BLocation? {
        return try {
            BLocationManager.get().getLocation(userId, pkg)
        } catch (e: Exception) {
            Log.w(TAG, "Error reading location for $pkg: ${e.message}")
            null
        }
    }

    fun setLocation(userId: Int, pkg: String, location: BLocation) {
        try {
            BLocationManager.get().setLocation(userId, pkg, location)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting location for $pkg: ${e.message}")
        }
    }

    fun getInstalledAppList(
        userID: Int,
        appsFakeLiveData: MutableLiveData<List<FakeLocationBean>>
    ) {
        val installedList = mutableListOf<FakeLocationBean>()
        try {
            val pm = BlackBoxCore.getPackageManager()
            val installedApplications: List<ApplicationInfo> =
                BlackBoxCore.get().getInstalledApplications(0, userID) ?: emptyList()

            val fallbackIcon: Drawable? = ContextCompat.getDrawable(App.getContext(), R.drawable.ic_brand_logo)

            for (installedApplication in installedApplications) {
                try {
                    val pkg = installedApplication.packageName ?: continue
                    val label = runCatching {
                        installedApplication.loadLabel(pm).toString()
                    }.getOrDefault(pkg)
                    val icon = runCatching {
                        installedApplication.loadIcon(pm)
                    }.getOrNull() ?: fallbackIcon ?: continue

                    val info = FakeLocationBean(
                        userID,
                        label,
                        icon,
                        pkg,
                        getPattern(userID, pkg),
                        getLocation(userID, pkg)
                    )
                    installedList.add(info)
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping app in FakeLocationRepository: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading fake location apps: ${e.message}")
        }

        appsFakeLiveData.postValue(installedList)
    }
}
