package top.niunaijun.blackboxa.view.list

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import cbfg.rvadapter.RVAdapter
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.app.AppManager
import top.niunaijun.blackboxa.bean.InstalledAppBean
import top.niunaijun.blackboxa.data.AppsRepository
import top.niunaijun.blackboxa.databinding.ActivityListBinding
import top.niunaijun.blackboxa.util.InjectionUtil
import top.niunaijun.blackboxa.util.inflate
import top.niunaijun.blackboxa.view.base.BaseActivity

class ListActivity : BaseActivity() {

    private val viewBinding: ActivityListBinding by inflate()
    private lateinit var mAdapter: RVAdapter<InstalledAppBean>
    private lateinit var viewModel: ListViewModel

    private var appList: List<InstalledAppBean> = emptyList()
    private var includeSystemApps = false
    private var userId = AppsRepository.ALL_USERS_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)
        setupEdgeToEdge(viewBinding.headerContainer, viewBinding.recyclerView)

        userId = intent.getIntExtra("userID", AppsRepository.ALL_USERS_ID)
        initToolbar(viewBinding.toolbarLayout.toolbar, R.string.choose_app, true)
        viewBinding.toolbarLayout.toolbar.subtitle = if (userId == AppsRepository.ALL_USERS_ID) {
            getString(R.string.all_clones_title)
        } else {
            AppManager.getSpaceDisplayName(userId)
        }

        mAdapter = RVAdapter<InstalledAppBean>(this, ListAdapter())
            .bind(viewBinding.recyclerView)
            .setItemClickListener { _, item, _ -> finishWithResult(item.packageName) }

        viewBinding.recyclerView.layoutManager = LinearLayoutManager(this)

        initControls()
        initViewModel()
    }

    private fun initControls() {
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

        viewBinding.switchAutoPin.isChecked = AppManager.mBlackBoxLoader.autoPinShortcut()
        viewBinding.switchAutoPin.setOnCheckedChangeListener { _, isChecked ->
            AppManager.mBlackBoxLoader.invalidAutoPinShortcut(isChecked)
        }

        viewBinding.chipShowSystem.setOnCheckedChangeListener { _, isChecked ->
            includeSystemApps = isChecked
            viewModel.getInstallAppList(userId, includeSystemApps)
        }
    }

    private fun initViewModel() {
        viewModel = ViewModelProvider(this, InjectionUtil.getListFactory())[ListViewModel::class.java]
        viewModel.getInstallAppList(userId, includeSystemApps)

        viewModel.loadingLiveData.observe(this) { loading ->
            viewBinding.loadingIndicator.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.appsLiveData.observe(this) { apps ->
            if (apps != null) {
                appList = apps
                filterApp(viewBinding.etSearch.text?.toString().orEmpty())
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

    private val openDocumentedResult =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { finishWithResult(it.toString()) }
        }

    private fun finishWithResult(source: String) {
        intent.putExtra("source", source)
        intent.putExtra("userID", userId)
        setResult(Activity.RESULT_OK, intent)
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        window.peekDecorView()?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
        finish()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.list_choose -> {
                openDocumentedResult.launch("application/vnd.android.package-archive")
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_list, menu)
        val tintColor = ContextCompat.getColor(this, R.color.mc_text_primary)
        menu?.findItem(R.id.list_choose)?.icon?.let { drawable ->
            val wrapped = DrawableCompat.wrap(drawable.mutate())
            DrawableCompat.setTint(wrapped, tintColor)
            menu.findItem(R.id.list_choose).icon = wrapped
        }
        return true
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, ListActivity::class.java))
        }
    }
}
