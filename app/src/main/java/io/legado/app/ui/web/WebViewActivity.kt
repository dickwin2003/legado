package io.legado.app.ui.web

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.*
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.databinding.ActivityWebViewBinding
import io.legado.app.model.localBook.LocalBook
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class WebViewActivity : VMBaseActivity<ActivityWebViewBinding, WebViewViewModel>() {
    
    override val binding by viewBinding(ActivityWebViewBinding::inflate)
    override val viewModel by viewModels<WebViewViewModel>()
    
    private var sourceUrl: String? = null
    
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        sourceUrl = intent.getStringExtra("url")
        val title = intent.getStringExtra("title") ?: "书源网站"
        
        binding.titleBar.title = title
        binding.titleBar.setNavigationOnClickListener { finish() }
        
        setupWebView()
        sourceUrl?.let { binding.webView.loadUrl(it) }
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
            }
            
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false
                    
                    // 检查是否是电子书文件
                    if (isEbookFile(url)) {
                        downloadAndOpenBook(url)
                        return true
                    }
                    
                    return false
                }
                
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // 注入JavaScript来处理下载链接
                    injectDownloadHandler()
                }
            }
            
            setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
                if (isEbookFile(url)) {
                    downloadAndOpenBook(url)
                } else {
                    // 使用系统浏览器下载
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    try {
                        startActivity(intent)
                    } catch (e: Exception) {
                        toastOnUi("无法下载文件: ${e.message}")
                    }
                }
            }
        }
    }
    
    private fun isEbookFile(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return lowerUrl.contains(".txt") || 
               lowerUrl.contains(".epub") || 
               lowerUrl.contains(".pdf") || 
               lowerUrl.contains(".mobi") || 
               lowerUrl.contains(".azw") || 
               lowerUrl.contains(".azw3") ||
               lowerUrl.contains(".umd")
    }
    
    private fun downloadAndOpenBook(url: String) {
        lifecycleScope.launch {
            try {
                toastOnUi("开始下载电子书...")
                
                // 简单的下载实现
                val fileName = url.substringAfterLast("/")
                val file = File(cacheDir, fileName)
                
                // 在后台线程下载
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val connection = URL(url).openConnection()
                    connection.connect()
                    
                    connection.getInputStream().use { input ->
                        FileOutputStream(file).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                
                // 导入到阅读应用
                val book = LocalBook.importFile(Uri.fromFile(file))
                book?.let {
                    toastOnUi("电子书下载成功，正在打开...")
                    startActivityForBook(it)
                } ?: toastOnUi("无法打开该电子书文件")
                
            } catch (e: Exception) {
                toastOnUi("下载失败: ${e.message}")
            }
        }
    }
    
    private fun injectDownloadHandler() {
        val javascript = """
            javascript:(function() {
                var links = document.getElementsByTagName('a');
                for (var i = 0; i < links.length; i++) {
                    var link = links[i];
                    var href = link.href.toLowerCase();
                    if (href.includes('.txt') || href.includes('.epub') || 
                        href.includes('.pdf') || href.includes('.mobi') || 
                        href.includes('.azw') || href.includes('.umd')) {
                        link.style.backgroundColor = '#ffeb3b';
                        link.style.padding = '2px 4px';
                        link.style.borderRadius = '3px';
                        link.title = '点击下载并用阅读应用打开';
                    }
                }
            })()
        """
        binding.webView.evaluateJavascript(javascript, null)
    }
    
    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
    
    companion object {
        fun start(context: Context, url: String, title: String? = null) {
            context.startActivity(Intent(context, WebViewActivity::class.java).apply {
                putExtra("url", url)
                putExtra("title", title)
            })
        }
    }
}
