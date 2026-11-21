package top.niunaijun.blackboxa.view.gms

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import cbfg.rvadapter.RVAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.bean.GmsBean
import top.niunaijun.blackboxa.databinding.ActivityGmsBinding
import top.niunaijun.blackboxa.util.InjectionUtil
import top.niunaijun.blackboxa.util.inflate
import top.niunaijun.blackboxa.util.toast
import top.niunaijun.blackboxa.view.base.LoadingActivity

class GmsManagerActivity : LoadingActivity() {

    private lateinit var viewModel: GmsViewModel
    private lateinit var mAdapter: RVAdapter<GmsBean>
    private val viewBinding: ActivityGmsBinding by inflate()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)
        setupEdgeToEdge(viewBinding.headerContainer, viewBinding.recyclerView)
        initToolbar(viewBinding.toolbarLayout.toolbar, R.string.gms_manager, true)
        initViewModel()
        initRecyclerView()
    }

    private fun initViewModel() {
        viewModel = ViewModelProvider(this, InjectionUtil.getGmsFactory())[GmsViewModel::class.java]
        showLoading()

        viewModel.mInstalledLiveData.observe(this) {
            hideLoading()
            mAdapter.setItems(it)
        }

        viewModel.mUpdateInstalledLiveData.observe(this) { result ->
            if (result == null) return@observe

            val items = mAdapter.getItems()
            for (index in items.indices) {
                val bean = items[index]
                if (bean.userID == result.userID) {
                    if (result.success) {
                        bean.isInstalledGms = !bean.isInstalledGms
                    }
                    mAdapter.replaceAt(index, bean)
                    break
                }
            }

            hideLoading()

            if (result.success) {
                toast(result.msg)
            } else {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.gms_manager)
                    .setMessage(result.msg)
                    .setPositiveButton(R.string.done, null)
                    .show()
            }
        }

        viewModel.getInstalledUser()
    }

    private fun initRecyclerView() {
        mAdapter = RVAdapter<GmsBean>(this, GmsAdapter()).bind(viewBinding.recyclerView)
            .setItemClickListener { view, item, _ ->
                val switchView = view.findViewById<MaterialSwitch>(R.id.checkbox)
                if (item.isInstalledGms) {
                    uninstallGms(item.userID, switchView)
                } else {
                    installGms(item.userID, switchView)
                }
            }
        viewBinding.recyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun installGms(userID: Int, switchView: MaterialSwitch) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.enable_gms)
            .setMessage(R.string.enable_gms_hint)
            .setPositiveButton(R.string.done) { _, _ ->
                showLoading()
                viewModel.installGms(userID)
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                switchView.isChecked = false
            }
            .setOnCancelListener {
                switchView.isChecked = false
            }
            .show()
    }

    private fun uninstallGms(userID: Int, switchView: MaterialSwitch) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.disable_gms)
            .setMessage(R.string.disable_gms_hint)
            .setPositiveButton(R.string.done) { _, _ ->
                showLoading()
                viewModel.uninstallGms(userID)
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                switchView.isChecked = true
            }
            .setOnCancelListener {
                switchView.isChecked = true
            }
            .show()
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, GmsManagerActivity::class.java))
        }
    }
}
