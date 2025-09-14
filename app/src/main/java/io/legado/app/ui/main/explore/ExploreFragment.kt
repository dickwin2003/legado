package io.legado.app.ui.main.explore

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.core.view.isGone
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.VMBaseFragment
import io.legado.app.constant.AppLog
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.databinding.FragmentExploreBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.ui.book.explore.ExploreShowActivity
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.book.search.SearchScope
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.utils.applyTint
import io.legado.app.utils.flowWithLifecycleAndDatabaseChange
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.startActivity
import io.legado.app.utils.transaction
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import io.legado.app.data.entities.BookSource
import io.legado.app.help.source.exploreKinds
import io.legado.app.utils.toastOnUi
import android.content.Intent
import android.net.Uri

/**
 * 发现界面
 */
class ExploreFragment() : VMBaseFragment<ExploreViewModel>(R.layout.fragment_explore),
    MainFragmentInterface,
    ExploreAdapter.CallBack {

    constructor(position: Int) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        arguments = bundle
    }

    override val position: Int? get() = arguments?.getInt("position")

    override val viewModel by viewModels<ExploreViewModel>()
    private val binding by viewBinding(FragmentExploreBinding::bind)
    private val adapter by lazy { ExploreAdapter(requireContext(), this) }
    private val searchView: SearchView by lazy {
        binding.titleBar.findViewById(R.id.search_view)
    }
    private val diffItemCallBack = ExploreDiffItemCallBack()
    private val groups = linkedSetOf<String>()
    private var exploreFlowJob: Job? = null
    private var groupsMenu: SubMenu? = null

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        setSupportToolbar(binding.titleBar.toolbar)
        initSearchView()
        initRecyclerView()
        initFab()
        initGroupData()
        upExploreData()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu) {
        super.onCompatCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.main_explore, menu)
        groupsMenu = menu.findItem(R.id.menu_group)?.subMenu
        upGroupsMenu()
    }

    override fun onCompatOptionsItemSelected(item: MenuItem) {
        when (item.itemId) {
            R.id.menu_manage_sources -> {
                startActivity<io.legado.app.ui.book.source.manage.BookSourceActivity>()
            }
            R.id.menu_add_source -> {
                showAddSourceDialog()
            }
            else -> {
                handleGroupMenuSelection(item)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        searchView.clearFocus()
    }

    private fun initSearchView() {
        searchView.applyTint(primaryTextColor)
        searchView.isSubmitButtonEnabled = true
        searchView.queryHint = getString(R.string.screen_find)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                upExploreData(newText)
                return false
            }
        })
    }

    private fun initRecyclerView() {
        binding.rvFind.setEdgeEffectColor(primaryColor)
        // 使用GridLayoutManager，在XML中已配置为每行4列，像RSS tab一样
        binding.rvFind.adapter = adapter
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                if (positionStart == 0) {
                    binding.rvFind.scrollToPosition(0)
                }
            }
        })
    }

    private fun initFab() {
        binding.fabAddSource.setOnClickListener {
            showAddSourceDialog()
        }
    }

    private fun initGroupData() {
        viewLifecycleOwner.lifecycleScope.launch {
            appDb.bookSourceDao.flowExploreGroups()
                .flowWithLifecycleAndDatabaseChange(
                    viewLifecycleOwner.lifecycle,
                    Lifecycle.State.RESUMED,
                    AppDatabase.BOOK_SOURCE_TABLE_NAME
                )
                .conflate()
                .distinctUntilChanged()
                .collect {
                    groups.clear()
                    groups.addAll(it)
                    upGroupsMenu()
                    delay(500)
                }
        }
    }

    private fun upExploreData(searchKey: String? = null) {
        exploreFlowJob?.cancel()
        exploreFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            when {
                searchKey.isNullOrBlank() -> {
                    appDb.bookSourceDao.flowExplore()
                }

                searchKey.startsWith("group:") -> {
                    val key = searchKey.substringAfter("group:")
                    appDb.bookSourceDao.flowGroupExplore(key)
                }

                else -> {
                    appDb.bookSourceDao.flowExplore(searchKey)
                }
            }.flowWithLifecycleAndDatabaseChange(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.RESUMED,
                AppDatabase.BOOK_SOURCE_TABLE_NAME
            ).catch {
                AppLog.put("发现界面更新数据出错", it)
            }.conflate().flowOn(IO).collect {
                val isEmpty = it.isEmpty() && searchView.query.isNullOrEmpty()
                binding.tvEmptyMsg.isGone = !isEmpty
                adapter.setItems(it, diffItemCallBack)
                delay(500)
            }
        }
    }

    private fun upGroupsMenu() = groupsMenu?.transaction { subMenu ->
        subMenu.removeGroup(R.id.menu_group_text)
        groups.forEach {
            subMenu.add(R.id.menu_group_text, Menu.NONE, Menu.NONE, it)
        }
    }

    override val scope: CoroutineScope
        get() = viewLifecycleOwner.lifecycleScope

    private fun handleGroupMenuSelection(item: MenuItem) {
        if (item.groupId == R.id.menu_group_text) {
            searchView.setQuery("group:${item.title}", true)
        }
    }

    override fun scrollTo(pos: Int) {
        binding.rvFind.scrollToPosition(pos)
    }

    override fun openExplore(sourceUrl: String, title: String, exploreUrl: String?) {
        if (exploreUrl.isNullOrBlank()) return
        startActivity<ExploreShowActivity> {
            putExtra("exploreName", title)
            putExtra("sourceUrl", sourceUrl)
            putExtra("exploreUrl", exploreUrl)
        }
    }

    override fun editSource(sourceUrl: String) {
        startActivity<BookSourceEditActivity> {
            putExtra("sourceUrl", sourceUrl)
        }
    }

    override fun toTop(source: BookSourcePart) {
        viewModel.topSource(source)
    }

    override fun deleteSource(source: BookSourcePart) {
        alert(R.string.draw) {
            setMessage(getString(R.string.sure_del) + "\n" + source.bookSourceName)
            noButton()
            yesButton {
                viewModel.deleteSource(source)
            }
        }
    }

    override fun searchBook(bookSource: BookSourcePart) {
        startActivity<SearchActivity> {
            putExtra("searchScope", SearchScope(bookSource).toString())
        }
    }

    fun compressExplore() {
        if (AppConfig.isEInkMode) {
            binding.rvFind.scrollToPosition(0)
        } else {
            binding.rvFind.smoothScrollToPosition(0)
        }
    }

    override fun openWebsite(sourceUrl: String) {
        AppLog.put("ExploreFragment - openWebsite被调用，URL: $sourceUrl")
        
        if (sourceUrl.isBlank()) {
            AppLog.put("ExploreFragment - URL为空，无法打开")
            toastOnUi("网址为空，无法打开")
            return
        }
        
        try {
            AppLog.put("ExploreFragment - 尝试使用内置WebView打开: $sourceUrl")
            // 使用内置WebView打开，支持电子书下载
            io.legado.app.ui.web.WebViewActivity.start(requireContext(), sourceUrl, "书源网站")
            AppLog.put("ExploreFragment - 内置WebView启动成功")
        } catch (e: Exception) {
            AppLog.put("ExploreFragment - 内置WebView启动失败，尝试使用系统浏览器", e)
            // 如果内置WebView失败，使用系统浏览器
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(sourceUrl))
                startActivity(intent)
                AppLog.put("ExploreFragment - 系统浏览器启动成功")
            } catch (e2: Exception) {
                AppLog.put("ExploreFragment - 系统浏览器启动失败", e2)
                toastOnUi("无法打开网站: ${e2.message}")
            }
        }
    }

    override fun openBookSource(bookSource: BookSourcePart) {
        AppLog.put("ExploreFragment - openBookSource被调用: ${bookSource.bookSourceName}")
        
        // 获取完整的BookSource对象来检查发现规则
        val fullBookSource = bookSource.getBookSource()
        if (fullBookSource != null && !fullBookSource.exploreUrl.isNullOrBlank()) {
            // 获取第一个发现分类
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val exploreKinds = fullBookSource.exploreKinds()
                    if (exploreKinds.isNotEmpty()) {
                        val firstKind = exploreKinds.first()
                        if (!firstKind.url.isNullOrBlank()) {
                            openExplore(bookSource.bookSourceUrl, firstKind.title, firstKind.url)
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    AppLog.put("获取发现分类失败", e)
                }
                
                // 如果没有发现分类或获取失败，打开书源网站
                openWebsite(bookSource.bookSourceUrl)
            }
        } else {
            // 没有发现规则，直接打开书源网站
            openWebsite(bookSource.bookSourceUrl)
        }
    }

    override fun disableSource(bookSource: BookSourcePart) {
        viewModel.disableSource(bookSource)
    }

    private fun showAddSourceDialog() {
        startActivity<BookSourceEditActivity>()
    }

}
