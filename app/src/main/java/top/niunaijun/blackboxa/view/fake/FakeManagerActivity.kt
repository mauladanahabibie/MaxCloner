package top.niunaijun.blackboxa.view.fake

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import cbfg.rvadapter.RVAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import top.niunaijun.blackbox.entity.location.BLocation
import top.niunaijun.blackbox.fake.frameworks.BLocationManager
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.app.AppManager
import top.niunaijun.blackboxa.bean.FakeLocationBean
import top.niunaijun.blackboxa.databinding.ActivityListBinding
import top.niunaijun.blackboxa.util.InjectionUtil
import top.niunaijun.blackboxa.util.inflate
import top.niunaijun.blackboxa.util.toast
import top.niunaijun.blackboxa.view.base.BaseActivity

class FakeManagerActivity : BaseActivity() {

    private val viewBinding: ActivityListBinding by inflate()
    private lateinit var mAdapter: RVAdapter<FakeLocationBean>
    private lateinit var viewModel: FakeLocationViewModel

    private var appList: List<FakeLocationBean> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)
        setupEdgeToEdge(viewBinding.headerContainer, viewBinding.recyclerView)

        initToolbar(viewBinding.toolbarLayout.toolbar, R.string.fake_location, true)
        viewBinding.toolbarLayout.toolbar.subtitle = AppManager.getSpaceDisplayName(currentUserID())

        viewBinding.autoPinBar.visibility = View.GONE
        viewBinding.chipShowSystem.visibility = View.GONE
        viewBinding.tvListHelperHint.visibility = View.VISIBLE
        viewBinding.tvListHelperHint.setText(R.string.fake_location_subtitle)
        viewBinding.etSearch.setHint(R.string.search_clones_hint)

        mAdapter = RVAdapter<FakeLocationBean>(this, FakeLocationAdapter())
            .bind(viewBinding.recyclerView)
            .setItemClickListener { _, data, _ ->
                val intent = Intent(this, FollowMyLocationOverlay::class.java).apply {
                    putExtra("location", data.fakeLocation)
                    putExtra("pkg", data.packageName)
                }
                locationResult.launch(intent)
            }
            .setItemLongClickListener { _, item, position ->
                disableFakeLocation(item, position)
            }

        viewBinding.recyclerView.layoutManager = LinearLayoutManager(this)

        initSearch()
        initViewModel()
    }

    private fun disableFakeLocation(item: FakeLocationBean, position: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.close_fake_location)
            .setMessage(getString(R.string.close_app_fake_location, item.name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.done) { _, _ ->
                BLocationManager.disableFakeLocation(currentUserID(), item.packageName)
                toast(getString(R.string.close_fake_location_success, item.name))
                item.fakeLocationPattern = BLocationManager.CLOSE_MODE
                mAdapter.replaceAt(position, item)
            }
            .show()
    }

    private fun initSearch() {
        viewBinding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString().orEmpty()
                viewBinding.btnClearSearch.visibility =
                    if (query.isEmpty()) View.GONE else View.VISIBLE
                filterApp(query)
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        viewBinding.btnClearSearch.setOnClickListener {
            viewBinding.etSearch.setText("")
        }
    }

    private fun initViewModel() {
        viewModel = ViewModelProvider(
            this,
            InjectionUtil.getFakeLocationFactory()
        )[FakeLocationViewModel::class.java]

        loadAppList()

        viewModel.appsLiveData.observe(this) { apps ->
            viewBinding.loadingIndicator.visibility = View.GONE
            if (apps != null) {
                appList = apps
                filterApp(viewBinding.etSearch.text?.toString().orEmpty())
            }
        }
    }

    private fun loadAppList() {
        viewBinding.loadingIndicator.visibility = View.VISIBLE
        viewModel.getInstallAppList(currentUserID())
    }

    private val locationResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.let { data ->
                    val latitude = data.getDoubleExtra("latitude", 0.0)
                    val longitude = data.getDoubleExtra("longitude", 0.0)
                    val pkg = data.getStringExtra("pkg") ?: return@let

                    viewBinding.loadingIndicator.visibility = View.VISIBLE
                    viewModel.saveLocationAndReload(
                        currentUserID(),
                        pkg,
                        BLocationManager.OWN_MODE,
                        BLocation(latitude, longitude)
                    )
                    toast(getString(R.string.set_location, latitude.toString(), longitude.toString()))
                }
            }
        }

    private fun filterApp(query: String) {
        val trimmed = query.trim()
        val filtered = if (trimmed.isEmpty()) {
            appList
        } else {
            appList.filter {
                it.name.contains(trimmed, ignoreCase = true) ||
                    it.packageName.contains(trimmed, ignoreCase = true)
            }
        }
        mAdapter.setItems(filtered)

        val isLoading = viewBinding.loadingIndicator.visibility == View.VISIBLE
        if (filtered.isEmpty() && !isLoading) {
            viewBinding.emptySearchContainer.visibility = View.VISIBLE
            viewBinding.recyclerView.visibility = View.GONE
        } else {
            viewBinding.emptySearchContainer.visibility = View.GONE
            viewBinding.recyclerView.visibility = View.VISIBLE
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, FakeManagerActivity::class.java))
        }
    }
}
