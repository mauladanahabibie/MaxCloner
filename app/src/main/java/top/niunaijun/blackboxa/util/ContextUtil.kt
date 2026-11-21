package top.niunaijun.blackboxa.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

object ContextUtil {

    fun Context.openAppSystemSettings() {
        startActivity(Intent().apply {
            action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            data = Uri.fromParts("package", packageName, null)
        })
    }

    fun Context.openShortcutPermissionSettings() {
        val brandInfo = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()
        val isXiaomiFamily = brandInfo.contains("xiaomi") ||
            brandInfo.contains("redmi") ||
            brandInfo.contains("poco")

        if (isXiaomiFamily) {
            val miuiCandidates = listOf(
                Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                    component = ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.permissions.PermissionsEditorActivity"
                    )
                    putExtra("extra_pkgname", packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
                Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                    putExtra("extra_pkgname", packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            for (candidate in miuiCandidates) {
                if (runCatching { startActivity(candidate) }.isSuccess) {
                    return
                }
            }
        }
        openAppSystemSettings()
    }
}
