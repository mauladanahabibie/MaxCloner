package top.niunaijun.blackboxa.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.view.main.BlackBoxLoader

object AppManager {
    private const val TAG = "AppManager"

    @JvmStatic
    val mBlackBoxLoader by lazy {
        try {
            BlackBoxLoader()
        } catch (e: Exception) {
            Log.e(TAG, "Error creating BlackBoxLoader: ${e.message}")
            BlackBoxLoader()
        }
    }

    @JvmStatic
    val mBlackBoxCore by lazy {
        try {
            mBlackBoxLoader.getBlackBoxCore()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting BlackBoxCore: ${e.message}")
            throw e
        }
    }

    @JvmStatic
    val mRemarkSharedPreferences: SharedPreferences by lazy {
        try {
            App.getContext().getSharedPreferences("UserRemark", Context.MODE_PRIVATE)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating SharedPreferences: ${e.message}")
            throw e
        }
    }

    fun getCustomSpaceRemark(userId: Int): String? {
        return try {
            val saved = mRemarkSharedPreferences.getString("Remark$userId", null)?.trim()
            val defaultSpaceLabel = App.getContext().getString(R.string.space_label, userId + 1)
            if (saved.isNullOrBlank() || saved == "User $userId" || saved == defaultSpaceLabel) {
                null
            } else {
                saved
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading space remark: ${e.message}")
            null
        }
    }

    fun getSpaceDisplayName(userId: Int): String {
        return try {
            getCustomSpaceRemark(userId)
                ?: App.getContext().getString(R.string.space_label, userId + 1)
        } catch (e: Exception) {
            "Space ${userId + 1}"
        }
    }

    fun doAttachBaseContext(context: Context) {
        try {
            mBlackBoxLoader.attachBaseContext(context)
            mBlackBoxLoader.addLifecycleCallback()
        } catch (e: Exception) {
            Log.e(TAG, "Error in doAttachBaseContext: ${e.message}")
        }
    }

    fun doOnCreate(context: Context) {
        try {
            mBlackBoxLoader.doOnCreate(context)
            initThirdService(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error in doOnCreate: ${e.message}")
        }
    }

    private fun initThirdService(context: Context) {
        try {
        } catch (e: Exception) {
            Log.e(TAG, "Error in initThirdService: ${e.message}")
        }
    }
}
