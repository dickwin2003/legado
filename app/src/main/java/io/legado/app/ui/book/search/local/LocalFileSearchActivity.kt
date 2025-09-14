package io.legado.app.ui.book.search.local

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.flexbox.FlexboxLayoutManager
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.databinding.ActivityLocalFileSearchBinding
import io.legado.app.lib.permission.Permissions
import io.legado.app.lib.permission.PermissionsCompat
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.model.localBook.LocalBook
import io.legado.app.utils.applyTint
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import android.net.Uri
import java.io.File

/**
 * 本地文件搜索Activity
 */
class LocalFileSearchActivity : VMBaseActivity<ActivityLocalFileSearchBinding, LocalFileSearchViewModel>(),
    LocalFileSearchAdapter.CallBack {

    override val binding by viewBinding(ActivityLocalFileSearchBinding::inflate)
    override val viewModel by viewModels<LocalFileSearchViewModel>()
    
    private val adapter by lazy { LocalFileSearchAdapter(this, this) }
    private val searchView: SearchView by lazy {
        binding.root.findViewById<SearchView>(R.id.search_view)
    }
    private var searchJob: Job? = null
    private var currentKeyword = ""

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.llContent.setBackgroundColor(backgroundColor)
        initRecyclerView()
        initSearchView()
        initOtherView()
        checkPermissions()
        receiptIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiptIntent(intent)
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.local_file_search, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_filter_txt -> filterByFileType("txt")
            R.id.menu_filter_epub -> filterByFileType("epub")
            R.id.menu_filter_pdf -> filterByFileType("pdf")
            R.id.menu_filter_all -> filterByFileType(null)
            R.id.menu_refresh -> startSearch(currentKeyword)
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun initSearchView() {
        searchView.applyTint(primaryTextColor)
        searchView.isSubmitButtonEnabled = true
        searchView.queryHint = getString(R.string.search_local_files)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                searchView.clearFocus()
                startSearch(query.trim())
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                // 延迟搜索，避免频繁搜索
                return false
            }
        })
        searchView.setOnQueryTextFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.tvHint.isVisible = true
                binding.recyclerView.isVisible = false
            }
        }
    }

    private fun initRecyclerView() {
        binding.recyclerView.setEdgeEffectColor(primaryColor)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun initOtherView() {
        binding.tvHint.text = "搜索手机中的电子书文件，支持按文件名搜索"
        binding.progressBar.visibility = View.GONE
        
        // 设置提示文字
        binding.tvSupportedFormats.text = "支持格式：TXT、EPUB、PDF、UMD、MOBI、AZW3、AZW、ZIP、RAR、7Z"
        
        // 添加快捷搜索按钮
        binding.btnQuickSearchAll.setOnClickListener { 
            searchView.setQuery("", true)
        }
        
        binding.btnQuickSearchTxt.setOnClickListener {
            filterByFileType("txt")
        }
        
        binding.btnQuickSearchEpub.setOnClickListener {
            filterByFileType("epub")
        }
        
        binding.btnQuickSearchPdf.setOnClickListener {
            filterByFileType("pdf")
        }
    }

    private fun checkPermissions() {
        PermissionsCompat.Builder()
            .addPermissions(*Permissions.Group.STORAGE)
            .rationale(R.string.tip_perm_request_storage)
            .onGranted {
                // 权限已授予，可以开始搜索
            }
            .onDenied {
                toastOnUi("需要存储权限才能搜索本地文件")
                finish()
            }
            .request()
    }

    private fun receiptIntent(intent: Intent? = null) {
        val key = intent?.getStringExtra("key")
        if (!key.isNullOrBlank()) {
            searchView.setQuery(key, true)
        } else {
            searchView.findViewById<TextView>(androidx.appcompat.R.id.search_src_text)
                .requestFocus()
        }
    }

    private fun startSearch(keyword: String) {
        currentKeyword = keyword
        if (keyword.isBlank()) {
            // 显示所有支持的文件
            searchAllFiles()
        } else {
            searchFiles(keyword)
        }
    }

    private fun searchFiles(keyword: String) {
        searchJob?.cancel()
        binding.progressBar.visibility = View.VISIBLE
        binding.tvHint.isVisible = false
        binding.recyclerView.isVisible = true
        
        searchJob = lifecycleScope.launch {
            viewModel.searchFlow(keyword)
                .catch { e ->
                    AppLog.put("本地文件搜索出错", e)
                    toastOnUi("搜索出错: ${e.localizedMessage}")
                    binding.progressBar.visibility = View.GONE
                }
                .collect { files ->
                    adapter.setItems(files)
                    binding.progressBar.visibility = View.GONE
                    
                    if (files.isEmpty()) {
                        binding.tvEmpty.isVisible = true
                        binding.tvEmpty.text = "未找到包含 \"$keyword\" 的文件"
                    } else {
                        binding.tvEmpty.isVisible = false
                    }
                }
        }
    }

    private fun searchAllFiles() {
        searchJob?.cancel()
        binding.progressBar.visibility = View.VISIBLE
        binding.tvHint.isVisible = false
        binding.recyclerView.isVisible = true
        
        searchJob = lifecycleScope.launch {
            viewModel.searchFlow("")
                .catch { e ->
                    AppLog.put("本地文件搜索出错", e)
                    toastOnUi("搜索出错: ${e.localizedMessage}")
                    binding.progressBar.visibility = View.GONE
                }
                .collect { files ->
                    adapter.setItems(files)
                    binding.progressBar.visibility = View.GONE
                    
                    if (files.isEmpty()) {
                        binding.tvEmpty.isVisible = true
                        binding.tvEmpty.text = "未找到支持的电子书文件"
                    } else {
                        binding.tvEmpty.isVisible = false
                    }
                }
        }
    }

    private fun filterByFileType(fileType: String?) {
        searchJob?.cancel()
        binding.progressBar.visibility = View.VISIBLE
        
        searchJob = lifecycleScope.launch {
            viewModel.searchFlow(currentKeyword, fileType)
                .catch { e ->
                    AppLog.put("本地文件搜索出错", e)
                    toastOnUi("搜索出错: ${e.localizedMessage}")
                    binding.progressBar.visibility = View.GONE
                }
                .collect { files ->
                    adapter.setItems(files)
                    binding.progressBar.visibility = View.GONE
                    
                    val filterText = fileType?.let { ".$it" } ?: "所有"
                    if (files.isEmpty()) {
                        binding.tvEmpty.isVisible = true
                        binding.tvEmpty.text = "未找到 $filterText 格式的文件"
                    } else {
                        binding.tvEmpty.isVisible = false
                    }
                }
        }
    }

    override fun observeLiveBus() {
        viewModel.searchProgressLiveData.observe(this) { progress ->
            binding.tvProgress.text = progress
        }
    }

    override fun openFile(file: LocalFileSearchViewModel.LocalBookFile) {
        lifecycleScope.launch {
            try {
                // 将文件添加到书架并打开
                val book = LocalBook.importFile(Uri.fromFile(file.file))
                book?.let { 
                    startActivityForBook(it)
                    finish()
                } ?: toastOnUi("无法打开文件")
            } catch (e: Exception) {
                AppLog.put("打开本地文件失败", e)
                toastOnUi("打开文件失败: ${e.localizedMessage}")
            }
        }
    }

    override fun addToBookShelf(file: LocalFileSearchViewModel.LocalBookFile) {
        lifecycleScope.launch {
            try {
                val book = LocalBook.importFile(Uri.fromFile(file.file))
                book?.let {
                    toastOnUi("已添加到书架")
                } ?: toastOnUi("添加失败")
            } catch (e: Exception) {
                AppLog.put("添加本地文件到书架失败", e)
                toastOnUi("添加失败: ${e.localizedMessage}")
            }
        }
    }

    companion object {
        fun start(context: Context, key: String? = null) {
            context.startActivity(Intent(context, LocalFileSearchActivity::class.java).apply {
                putExtra("key", key)
            })
        }
    }
}