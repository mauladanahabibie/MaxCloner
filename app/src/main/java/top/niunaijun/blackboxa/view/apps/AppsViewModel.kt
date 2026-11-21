package top.niunaijun.blackboxa.view.apps

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.MutableLiveData
import top.niunaijun.blackboxa.bean.AppInfo
import top.niunaijun.blackboxa.bean.InstallOutcome
import top.niunaijun.blackboxa.data.AppsRepository
import top.niunaijun.blackboxa.view.base.BaseViewModel

class AppsViewModel(private val repo: AppsRepository) : BaseViewModel() {

    val appsLiveData = MutableLiveData<List<AppInfo>>()

    val recentAppsLiveData = MutableLiveData<List<AppInfo>>()

    val outcomeLiveData = MutableLiveData<InstallOutcome?>()

    val launchLiveData = MutableLiveData<Boolean?>()

    val updateSortLiveData = MutableLiveData<Boolean>()

    fun getInstalledApps(userId: Int) {
        launchOnUI {
            repo.getVmInstallList(userId, appsLiveData, recentAppsLiveData)
        }
    }

    fun getInstalledAppsWithRetry(userId: Int, maxRetries: Int = 3) {
        var retryCount = 0

        fun attemptLoad() {
            launchOnUI {
                repo.getVmInstallList(userId, appsLiveData, recentAppsLiveData)
                val currentApps = appsLiveData.value
                if ((currentApps == null || currentApps.isEmpty()) && retryCount < maxRetries) {
                    retryCount++
                    Log.d("AppsViewModel", "No apps loaded, retrying... ($retryCount/$maxRetries)")
                    Handler(Looper.getMainLooper()).postDelayed({
                        attemptLoad()
                    }, 1000)
                }
            }
        }

        attemptLoad()
    }

    fun install(source: String, userID: Int) {
        launchOnUI {
            repo.installApk(source, userID, outcomeLiveData)
        }
    }

    fun unInstall(packageName: String, userID: Int) {
        launchOnUI {
            repo.unInstall(packageName, userID, outcomeLiveData)
        }
    }

    fun clearApkData(packageName: String, userID: Int) {
        launchOnUI {
            repo.clearApkData(packageName, userID, outcomeLiveData)
        }
    }

    fun launchApk(packageName: String, userID: Int) {
        launchOnUI {
            repo.launchApk(packageName, userID, launchLiveData)
        }
    }

    fun updateApkOrder(userID: Int, dataList: List<AppInfo>) {
        launchOnUI {
            repo.updateApkOrder(userID, dataList)
        }
    }
}
