package top.niunaijun.blackboxa.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import top.niunaijun.blackboxa.R

class ShortcutPinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pkg = intent?.getStringExtra(ShortcutUtil.EXTRA_PKG) ?: return
        val userId = intent.getIntExtra(ShortcutUtil.EXTRA_USER_ID, 0)
        val label = intent.getStringExtra(ShortcutUtil.EXTRA_LABEL) ?: pkg

        ShortcutUtil.markShortcutPinned(context, userId, pkg)
        Toast.makeText(
            context.applicationContext,
            context.getString(R.string.shortcut_added_toast, label),
            Toast.LENGTH_SHORT
        ).show()
    }
}
