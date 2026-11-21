package top.niunaijun.blackboxa.view.main

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.util.ShortcutUtil

class ShortcutActivity : AppCompatActivity() {

    companion object {
        const val ACTION_LAUNCH_CLONE = "com.maxcloner.app.ACTION_LAUNCH_CLONE"
        const val EXTRA_PKG = "pkg"
        const val EXTRA_USER_ID = "userId"
        private const val LAUNCH_URI_PREFIX = "maxcloner://launch/"

        fun parseLaunchTarget(
            pkgExtra: String?,
            userIdExtra: Int?,
            dataUriString: String?
        ): Pair<String, Int>? {
            val cleanPkg = pkgExtra?.trim()
            if (!cleanPkg.isNullOrEmpty()) {
                val safeUserId = (userIdExtra ?: 0).coerceAtLeast(0)
                return cleanPkg to safeUserId
            }

            val rawUri = dataUriString?.trim() ?: return null
            if (!rawUri.startsWith(LAUNCH_URI_PREFIX)) return null

            val remainder = rawUri.removePrefix(LAUNCH_URI_PREFIX)
            val parts = remainder.split("/", limit = 2)
            if (parts.size != 2) return null

            val parsedUserId = parts[0].toIntOrNull()?.takeIf { it >= 0 } ?: return null
            val parsedPkg = parts[1].trim().takeIf { it.isNotEmpty() } ?: return null
            return parsedPkg to parsedUserId
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rawUserId = if (intent.hasExtra(EXTRA_USER_ID)) {
            intent.getIntExtra(EXTRA_USER_ID, 0)
        } else {
            null
        }
        val target = parseLaunchTarget(
            pkgExtra = intent.getStringExtra(EXTRA_PKG),
            userIdExtra = rawUserId,
            dataUriString = intent.dataString
        )

        if (target == null) {
            finishAndTransition()
            return
        }

        val (pkg, userId) = target
        setContentView(R.layout.activity_shortcut_launch)

        val ivIcon = findViewById<ImageView>(R.id.ivShortcutIcon)
        val tvAppName = findViewById<TextView>(R.id.tvShortcutAppName)
        tvAppName.text = getString(R.string.opening_app_progress, pkg)

        lifecycleScope.launch {
            val appInfo = withContext(Dispatchers.IO) {
                ShortcutUtil.loadClonedAppInfo(this@ShortcutActivity, pkg, userId)
            }
            if (appInfo != null) {
                tvAppName.text = getString(R.string.opening_app_progress, appInfo.name)
                appInfo.icon?.let { ivIcon.setImageDrawable(it) }
            }

            val outcome = withContext(Dispatchers.IO) {
                val core = BlackBoxCore.get()
                var servicesReady = core.areServicesAvailable()
                var attempt = 0
                while (!servicesReady && attempt < 6) {
                    delay(250L)
                    servicesReady = core.areServicesAvailable()
                    attempt++
                }

                if (servicesReady && !core.isInstalled(pkg, userId)) {
                    return@withContext LaunchStatus.NOT_INSTALLED
                }

                var launched = runCatching { core.launchApk(pkg, userId) }.getOrDefault(false)
                if (!launched) {
                    delay(300L)
                    launched = runCatching { core.launchApk(pkg, userId) }.getOrDefault(false)
                }

                if (launched) LaunchStatus.LAUNCHED else LaunchStatus.FAILED
            }

            when (outcome) {
                LaunchStatus.NOT_INSTALLED -> {
                    Toast.makeText(
                        applicationContext,
                        R.string.shortcut_clone_missing,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                LaunchStatus.FAILED -> {
                    Toast.makeText(
                        applicationContext,
                        R.string.start_fail,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                LaunchStatus.LAUNCHED -> Unit
            }

            finishAndTransition()
        }
    }

    @Suppress("DEPRECATION")
    private fun finishAndTransition() {
        finish()
        overridePendingTransition(0, 0)
    }

    private enum class LaunchStatus {
        LAUNCHED,
        NOT_INSTALLED,
        FAILED
    }
}
