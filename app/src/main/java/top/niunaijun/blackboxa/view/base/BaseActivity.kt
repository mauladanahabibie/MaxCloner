package top.niunaijun.blackboxa.view.base

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

open class BaseActivity : AppCompatActivity() {

    protected fun initToolbar(
        toolbar: Toolbar,
        title: Int,
        showBack: Boolean = false,
        onBack: (() -> Unit)? = null
    ) {
        setSupportActionBar(toolbar)
        toolbar.setTitle(title)
        if (showBack) {
            supportActionBar?.let {
                it.setDisplayHomeAsUpEnabled(true)
                toolbar.setNavigationOnClickListener {
                    onBack?.invoke()
                    finish()
                }
            }
        }
    }

    protected fun setupEdgeToEdge(
        topView: View?,
        bottomView: View? = null,
        applyBottomAsMargin: Boolean = false
    ) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        val initialTopPadding = topView?.paddingTop ?: 0
        val initialBottomPadding = bottomView?.paddingBottom ?: 0
        val initialBottomMargin = (bottomView?.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0

        val target = topView ?: bottomView ?: window.decorView
        ViewCompat.setOnApplyWindowInsetsListener(target) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            topView?.let {
                it.setPadding(
                    it.paddingLeft,
                    initialTopPadding + insets.top,
                    it.paddingRight,
                    it.paddingBottom
                )
            }

            bottomView?.let { view ->
                if (applyBottomAsMargin) {
                    val lp = view.layoutParams as? ViewGroup.MarginLayoutParams
                    if (lp != null) {
                        lp.bottomMargin = initialBottomMargin + insets.bottom
                        view.layoutParams = lp
                    }
                } else {
                    view.setPadding(
                        view.paddingLeft,
                        view.paddingTop,
                        view.paddingRight,
                        initialBottomPadding + insets.bottom
                    )
                }
            }

            windowInsets
        }
        ViewCompat.requestApplyInsets(target)
    }

    protected fun currentUserID(): Int {
        return intent.getIntExtra("userID", 0)
    }
}
