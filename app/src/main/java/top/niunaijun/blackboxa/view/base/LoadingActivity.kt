package top.niunaijun.blackboxa.view.base

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import top.niunaijun.blackboxa.R

abstract class LoadingActivity : BaseActivity() {

    private var loadingDialog: MaxLoadingDialog? = null

    fun showLoading(message: String? = null) {
        if (isFinishing || isDestroyed) return
        val resolvedMsg = message ?: getString(R.string.loading_generic)
        val existing = supportFragmentManager.findFragmentByTag(LOADING_TAG) as? MaxLoadingDialog
        if (existing != null && existing.isAdded) {
            existing.updateMessage(resolvedMsg)
            loadingDialog = existing
            return
        }

        val dialog = MaxLoadingDialog.newInstance(resolvedMsg)
        loadingDialog = dialog
        supportFragmentManager.beginTransaction()
            .add(dialog, LOADING_TAG)
            .commitAllowingStateLoss()
    }

    fun hideLoading() {
        val existing = (supportFragmentManager.findFragmentByTag(LOADING_TAG) as? MaxLoadingDialog)
            ?: loadingDialog
        existing?.dismissAllowingStateLoss()
        loadingDialog = null
    }

    class MaxLoadingDialog : DialogFragment() {

        private var tvMessage: TextView? = null

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            dialog?.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isCancelable = false
            val view = inflater.inflate(R.layout.dialog_loading, container, false)
            tvMessage = view.findViewById(R.id.tvLoadingMessage)
            val initialMsg = arguments?.getString(ARG_MESSAGE)
            if (!initialMsg.isNullOrBlank()) {
                tvMessage?.text = initialMsg
            }
            return view
        }

        fun updateMessage(message: String) {
            arguments?.putString(ARG_MESSAGE, message)
            tvMessage?.text = message
        }

        override fun onDestroyView() {
            tvMessage = null
            super.onDestroyView()
        }

        companion object {
            private const val ARG_MESSAGE = "arg_message"

            fun newInstance(message: String): MaxLoadingDialog {
                return MaxLoadingDialog().apply {
                    arguments = Bundle().apply {
                        putString(ARG_MESSAGE, message)
                    }
                }
            }
        }
    }

    companion object {
        private const val LOADING_TAG = "maxcloner_loading_dialog"
    }
}
