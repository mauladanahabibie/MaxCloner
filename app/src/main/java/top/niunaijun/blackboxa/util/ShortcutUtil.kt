package top.niunaijun.blackboxa.util

import android.app.AppOpsManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Process
import android.util.Log
import android.view.LayoutInflater
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.app.AppManager
import top.niunaijun.blackboxa.bean.AppInfo
import top.niunaijun.blackboxa.databinding.DialogShortcutCustomizeBinding
import top.niunaijun.blackboxa.util.ContextUtil.openShortcutPermissionSettings
import top.niunaijun.blackboxa.view.main.ShortcutActivity

object ShortcutUtil {

    private const val TAG = "ShortcutUtil"
    private const val PREF_PINS = "ShortcutPins"
    private const val ICON_SIZE_PX = 192
    private const val MIUI_OP_INSTALL_SHORTCUT = 10017

    const val EXTRA_PKG = "extra_pkg"
    const val EXTRA_USER_ID = "extra_user_id"
    const val EXTRA_LABEL = "extra_label"

    enum class PinResult {
        REQUESTED,
        PERMISSION_REQUIRED,
        UPDATED,
        UNSUPPORTED
    }

    fun buildShortcutId(userId: Int, packageName: String): String {
        return "clone_${userId}_${packageName}"
    }

    private fun buildDynamicShortcutId(userId: Int, packageName: String): String {
        return "dyn_${userId}_${packageName}"
    }

    private fun buildLegacyShortcutId(userId: Int, packageName: String): String {
        return "$packageName$userId"
    }

    fun buildLaunchUri(userId: Int, packageName: String): Uri {
        return Uri.parse("maxcloner://launch/$userId/$packageName")
    }

    fun defaultShortcutLabel(
        appName: String,
        userId: Int,
        customSpaceRemark: String? = null
    ): String {
        val cleanName = appName.trim().ifEmpty { "App" }
        val cleanRemark = customSpaceRemark?.trim()
        return when {
            !cleanRemark.isNullOrEmpty() -> "$cleanName ($cleanRemark)"
            userId <= 0 -> cleanName
            else -> "$cleanName #${userId + 1}"
        }
    }

    fun isMiuiShortcutPermissionDenied(context: Context): Boolean {
        val brandInfo = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()
        val isXiaomiFamily = brandInfo.contains("xiaomi") ||
            brandInfo.contains("redmi") ||
            brandInfo.contains("poco")
        if (!isXiaomiFamily) return false

        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return false
            val checkMethod = AppOpsManager::class.java.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java
            )
            val mode = checkMethod.invoke(
                appOps,
                MIUI_OP_INSTALL_SHORTCUT,
                Process.myUid(),
                context.packageName
            ) as? Int ?: return false
            mode != AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            Log.w(TAG, "Unable to query MIUI shortcut AppOp: ${e.message}")
            false
        }
    }

    fun showShortcutPermissionDialog(context: Context) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.shortcut_perm_dialog_title)
            .setMessage(R.string.shortcut_perm_dialog_message)
            .setPositiveButton(R.string.open_permission_settings) { _, _ ->
                context.openShortcutPermissionSettings()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun buildShortcutIcon(
        context: Context,
        drawable: Drawable?,
        userId: Int,
        showBadge: Boolean = true
    ): IconCompat {
        val resolvedDrawable = drawable ?: ContextCompat.getDrawable(context, R.drawable.ic_brand_logo)
        val bitmap = Bitmap.createBitmap(ICON_SIZE_PX, ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        resolvedDrawable?.let {
            it.setBounds(0, 0, ICON_SIZE_PX, ICON_SIZE_PX)
            it.draw(canvas)
        }

        if (showBadge) {
            val badgeRadius = ICON_SIZE_PX * 0.17f
            val cx = ICON_SIZE_PX - badgeRadius - 6f
            val cy = ICON_SIZE_PX - badgeRadius - 6f

            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
                style = Paint.Style.FILL
            }
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#2563EB")
                style = Paint.Style.FILL
            }
            val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#22C55E")
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = badgeRadius * 1.15f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }

            canvas.drawCircle(cx, cy, badgeRadius + 4f, ringPaint)
            canvas.drawCircle(cx, cy, badgeRadius, fillPaint)
            canvas.drawCircle(cx, cy, badgeRadius, accentPaint)

            val label = (userId + 1).toString()
            val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(label, cx, textY, textPaint)
        }

        return IconCompat.createWithBitmap(bitmap)
    }

    fun requestAutoPinShortcut(
        context: Context,
        userId: Int,
        info: AppInfo,
        customLabel: String? = null,
        showBadge: Boolean = true,
        forceNewIdIfPinned: Boolean = false
    ): PinResult {
        if (isMiuiShortcutPermissionDenied(context)) {
            return PinResult.PERMISSION_REQUIRED
        }

        val remark = AppManager.getCustomSpaceRemark(userId)
        val resolvedLabel = customLabel?.trim()?.takeIf { it.isNotEmpty() }
            ?: defaultShortcutLabel(info.name, userId, remark)

        val iconCompat = buildShortcutIcon(context, info.icon, userId, showBadge)
        val dynamicShortcut = buildShortcutInfoCompat(
            context = context,
            shortcutId = buildDynamicShortcutId(userId, info.packageName),
            userId = userId,
            packageName = info.packageName,
            label = resolvedLabel,
            iconCompat = iconCompat
        )
        publishDynamicShortcut(context, dynamicShortcut)

        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            return PinResult.UNSUPPORTED
        }

        val alreadyPinned = isShortcutPinned(context, userId, info.packageName)
        val baseId = buildShortcutId(userId, info.packageName)

        if (alreadyPinned && !forceNewIdIfPinned) {
            val updateTarget = buildShortcutInfoCompat(
                context = context,
                shortcutId = baseId,
                userId = userId,
                packageName = info.packageName,
                label = resolvedLabel,
                iconCompat = iconCompat
            )
            runCatching { ShortcutManagerCompat.updateShortcuts(context, listOf(updateTarget)) }
            return PinResult.UPDATED
        }

        val pinId = if (alreadyPinned) "${baseId}_${System.currentTimeMillis()}" else baseId
        val pinShortcutInfo = buildShortcutInfoCompat(
            context = context,
            shortcutId = pinId,
            userId = userId,
            packageName = info.packageName,
            label = resolvedLabel,
            iconCompat = iconCompat
        )

        val callbackIntent = Intent(context, ShortcutPinReceiver::class.java).apply {
            putExtra(EXTRA_PKG, info.packageName)
            putExtra(EXTRA_USER_ID, userId)
            putExtra(EXTRA_LABEL, resolvedLabel)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val callbackSender = PendingIntent.getBroadcast(
            context,
            pinId.hashCode(),
            callbackIntent,
            flags
        ).intentSender

        val requested = runCatching {
            ShortcutManagerCompat.requestPinShortcut(context, pinShortcutInfo, callbackSender)
        }.getOrDefault(false)

        if (!requested) {
            return PinResult.UNSUPPORTED
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            markShortcutPinned(context, userId, info.packageName)
        }
        return PinResult.REQUESTED
    }

    fun showCustomizeShortcutDialog(
        context: Context,
        userId: Int,
        info: AppInfo,
        onResult: ((PinResult) -> Unit)? = null
    ) {
        val binding = DialogShortcutCustomizeBinding.inflate(LayoutInflater.from(context))
        val remark = AppManager.getCustomSpaceRemark(userId)
        val defaultLabel = defaultShortcutLabel(info.name, userId, remark)
        binding.etShortcutLabel.setText(defaultLabel)
        binding.etShortcutLabel.setSelection(defaultLabel.length)
        binding.switchShowBadge.isChecked = true

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.app_shortcut)
            .setView(binding.root)
            .setPositiveButton(R.string.add_to_home_screen) { _, _ ->
                val chosenLabel = binding.etShortcutLabel.text?.toString()?.trim()
                val showBadge = binding.switchShowBadge.isChecked
                val result = requestAutoPinShortcut(
                    context = context,
                    userId = userId,
                    info = info,
                    customLabel = chosenLabel,
                    showBadge = showBadge,
                    forceNewIdIfPinned = true
                )
                onResult?.invoke(result)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun isShortcutPinned(context: Context, userId: Int, packageName: String): Boolean {
        val exactId = buildShortcutId(userId, packageName)
        val prefix = "${exactId}_"
        val legacyId = buildLegacyShortcutId(userId, packageName)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val manager = context.getSystemService(ShortcutManager::class.java)
                if (manager != null) {
                    return manager.pinnedShortcuts.any { shortcut ->
                        shortcut.isEnabled && (
                            shortcut.id == exactId ||
                                shortcut.id.startsWith(prefix) ||
                                shortcut.id == legacyId
                            )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error querying pinned shortcuts: ${e.message}")
            }
        }
        return context.getSharedPreferences(PREF_PINS, Context.MODE_PRIVATE)
            .getBoolean(exactId, false)
    }

    fun markShortcutPinned(context: Context, userId: Int, packageName: String) {
        context.getSharedPreferences(PREF_PINS, Context.MODE_PRIVATE).edit {
            putBoolean(buildShortcutId(userId, packageName), true)
        }
    }

    fun disableShortcut(context: Context, userId: Int, packageName: String) {
        val exactId = buildShortcutId(userId, packageName)
        val prefix = "${exactId}_"
        val ids = mutableSetOf(
            exactId,
            buildDynamicShortcutId(userId, packageName),
            buildLegacyShortcutId(userId, packageName)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val manager = context.getSystemService(ShortcutManager::class.java)
                manager?.pinnedShortcuts?.forEach { shortcut ->
                    if (shortcut.id == exactId || shortcut.id.startsWith(prefix)) {
                        ids.add(shortcut.id)
                    }
                }
            }
        }
        val idList = ids.toList()
        try {
            ShortcutManagerCompat.disableShortcuts(
                context,
                idList,
                context.getString(R.string.shortcut_disabled_msg)
            )
            ShortcutManagerCompat.removeDynamicShortcuts(context, idList)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to disable shortcuts for $packageName: ${e.message}")
        }
        context.getSharedPreferences(PREF_PINS, Context.MODE_PRIVATE).edit {
            remove(exactId)
        }
    }

    fun loadClonedAppInfo(context: Context, packageName: String, userId: Int): AppInfo? {
        return try {
            val bAppInfo = BlackBoxCore.getBPackageManager().getApplicationInfo(packageName, 0, userId)
            val pm = context.packageManager
            if (bAppInfo != null) {
                val label = runCatching { pm.getApplicationLabel(bAppInfo).toString() }
                    .getOrDefault(packageName)
                val icon = runCatching { pm.getApplicationIcon(bAppInfo) }.getOrNull()
                AppInfo(label, icon, packageName, bAppInfo.sourceDir ?: "", false)
            } else {
                val hostInfo = pm.getApplicationInfo(packageName, 0)
                val label = pm.getApplicationLabel(hostInfo).toString()
                val icon = pm.getApplicationIcon(hostInfo)
                AppInfo(label, icon, packageName, hostInfo.sourceDir ?: "", false)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not load cloned AppInfo for $packageName: ${e.message}")
            null
        }
    }

    private fun buildShortcutInfoCompat(
        context: Context,
        shortcutId: String,
        userId: Int,
        packageName: String,
        label: String,
        iconCompat: IconCompat
    ): ShortcutInfoCompat {
        val launchIntent = Intent(context, ShortcutActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            putExtra(ShortcutActivity.EXTRA_PKG, packageName)
            putExtra(ShortcutActivity.EXTRA_USER_ID, userId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        return ShortcutInfoCompat.Builder(context, shortcutId)
            .setIntent(launchIntent)
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(iconCompat)
            .build()
    }

    private fun publishDynamicShortcut(context: Context, shortcutInfo: ShortcutInfoCompat) {
        try {
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcutInfo)
        } catch (e: Exception) {
            Log.w(TAG, "Could not publish dynamic shortcut: ${e.message}")
        }
    }
}
