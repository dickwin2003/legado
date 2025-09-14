package io.legado.app.ui.association

import android.app.Application
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import com.jayway.jsonpath.JsonPath
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.config.AppConfig
import io.legado.app.help.http.decompressed
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.source.SourceHelp
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.inputStream
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.isJsonObject
import io.legado.app.utils.isUri
import io.legado.app.utils.splitNotBlank


class ImportBookSourceViewModel(app: Application) : BaseViewModel(app) {
    var isAddGroup = false
    var groupName: String? = null
    val errorLiveData = MutableLiveData<String>()
    val successLiveData = MutableLiveData<Int>()

    val allSources = arrayListOf<BookSource>()
    val checkSources = arrayListOf<BookSourcePart?>()
    val selectStatus = arrayListOf<Boolean>()
    val newSourceStatus = arrayListOf<Boolean>()
    val updateSourceStatus = arrayListOf<Boolean>()

    val isSelectAll: Boolean
        get() {
            selectStatus.forEach {
                if (!it) {
                    return false
                }
            }
            return true
        }

    val isSelectAllNew: Boolean
        get() {
            newSourceStatus.forEachIndexed { index, b ->
                if (b && !selectStatus[index]) {
                    return false
                }
            }
            return true
        }

    val isSelectAllUpdate: Boolean
        get() {
            updateSourceStatus.forEachIndexed { index, b ->
                if (b && !selectStatus[index]) {
                    return false
                }
            }
            return true
        }

    val selectCount: Int
        get() {
            var count = 0
            selectStatus.forEach {
                if (it) {
                    count++
                }
            }
            return count
        }

    /**
     * 执行选中书源的导入操作
     * 根据用户配置处理书源属性保留、分组设置等
     * 
     * @param finally 导入完成后的回调函数
     */
    fun importSelect(finally: () -> Unit) {
        execute {
            val group = groupName?.trim()
            // 获取用户配置的保留选项
            val keepName = AppConfig.importKeepName
            val keepGroup = AppConfig.importKeepGroup
            val keepEnable = AppConfig.importKeepEnable
            val selectSource = arrayListOf<BookSource>()
            
            // 处理每个选中的书源
            selectStatus.forEachIndexed { index, b ->
                if (b) {
                    val source = allSources[index]
                    // 如果本地已存在该书源，根据配置保留原有属性
                    checkSources[index]?.let {
                        if (keepName) {
                            source.bookSourceName = it.bookSourceName
                        }
                        if (keepGroup) {
                            source.bookSourceGroup = it.bookSourceGroup
                        }
                        if (keepEnable) {
                            source.enabled = it.enabled
                            source.enabledExplore = it.enabledExplore
                        }
                        source.customOrder = it.customOrder
                    }
                    
                    // 处理分组设置
                    if (!group.isNullOrEmpty()) {
                        if (isAddGroup) {
                            val groups = linkedSetOf<String>()
                            source.bookSourceGroup?.splitNotBlank(AppPattern.splitGroupRegex)?.let {
                                groups.addAll(it)
                            }
                            groups.add(group)
                            source.bookSourceGroup = groups.joinToString(",")
                        } else {
                            source.bookSourceGroup = group
                        }
                    }
                    selectSource.add(source)
                }
            }
            SourceHelp.insertBookSource(*selectSource.toTypedArray())
            ContentProcessor.upReplaceRules()
        }.onFinally {
            finally.invoke()
        }
    }

    /**
     * 导入书源数据
     * 支持多种格式：JSON对象、JSON数组、URL链接、URI路径
     * 
     * @param text 要导入的书源数据，可以是：
     *             - JSON对象：包含sourceUrls数组的订阅格式或单个书源
     *             - JSON数组：书源列表
     *             - URL：网络书源链接
     *             - URI：本地文件路径
     */
    fun importSource(text: String) {
        execute {
            val mText = text.trim()
            when {
                // 处理JSON对象格式的数据
                mText.isJsonObject() -> {
                    kotlin.runCatching {
                        // 尝试解析为订阅格式（包含sourceUrls数组）
                        val json = JsonPath.parse(mText)
                        json.read<List<String>>("$.sourceUrls")
                    }.onSuccess { listUrl ->
                        // 批量导入订阅中的所有书源URL
                        listUrl.forEach {
                            importSourceUrl(it)
                        }
                    }.onFailure {
                        // 解析为单个书源对象
                        GSON.fromJsonObject<BookSource>(mText).getOrThrow().let {
                            if (it.bookSourceUrl.isEmpty()) {
                                throw NoStackTraceException("不是书源")
                            }
                            allSources.add(it)
                        }
                    }
                }

                // 处理JSON数组格式的书源列表
                mText.isJsonArray() -> GSON.fromJsonArray<BookSource>(mText).getOrThrow()
                    .let { items ->
                        val source = items.firstOrNull() ?: return@let
                        if (source.bookSourceUrl.isEmpty()) {
                            throw NoStackTraceException("不是书源")
                        }
                        allSources.addAll(items)
                    }

                // 处理网络URL
                mText.isAbsUrl() -> {
                    importSourceUrl(mText)
                }

                // 处理本地文件URI
                mText.isUri() -> {
                    val uri = Uri.parse(mText)
                    uri.inputStream(context).getOrThrow().use { inputS ->
                        GSON.fromJsonArray<BookSource>(inputS).getOrThrow().let {
                            val source = it.firstOrNull() ?: return@let
                            if (source.bookSourceUrl.isEmpty()) {
                                throw NoStackTraceException("不是书源")
                            }
                            allSources.addAll(it)
                        }
                    }
                }

                else -> throw NoStackTraceException(context.getString(R.string.wrong_format))
            }
        }.onError {
            errorLiveData.postValue("ImportError:${it.localizedMessage}")
            AppLog.put("ImportError:${it.localizedMessage}", it)
        }.onSuccess {
            comparisonSource()
        }
    }

    /**
     * 从网络URL导入书源
     * 支持特殊处理：如果URL以#requestWithoutUA结尾，则不发送User-Agent头
     * 
     * @param url 书源的网络地址
     * @throws NoStackTraceException 当URL返回的不是有效书源时
     */
    private suspend fun importSourceUrl(url: String) {
        okHttpClient.newCallResponseBody {
            if (url.endsWith("#requestWithoutUA")) {
                // 移除UA标识并设置空User-Agent
                url(url.substringBeforeLast("#requestWithoutUA"))
                header(AppConst.UA_NAME, "null")
            } else {
                url(url)
            }
        }.decompressed().byteStream().use {
            // 解析响应数据为书源列表
            GSON.fromJsonArray<BookSource>(it).getOrThrow().let { list ->
                val source = list.firstOrNull() ?: return@let
                if (source.bookSourceUrl.isEmpty()) {
                    throw NoStackTraceException("不是书源")
                }
                allSources.addAll(list)
            }
        }
    }

    /**
     * 对比导入的书源与本地已有书源
     * 为每个书源设置相应的状态标记：
     * - selectStatus: 是否默认选中（新书源或需要更新的书源默认选中）
     * - newSourceStatus: 是否为新书源
     * - updateSourceStatus: 是否为更新书源
     */
    private fun comparisonSource() {
        execute {
            allSources.forEach {
                // 查询本地是否已存在该书源
                val source = appDb.bookSourceDao.getBookSourcePart(it.bookSourceUrl)
                checkSources.add(source)
                
                // 新书源或更新时间更新的书源默认选中
                selectStatus.add(source == null || source.lastUpdateTime < it.lastUpdateTime)
                
                // 标记新增书源
                newSourceStatus.add(source == null)
                
                // 标记需要更新的书源
                updateSourceStatus.add(source != null && source.lastUpdateTime < it.lastUpdateTime)
            }
            // 通知UI更新
            successLiveData.postValue(allSources.size)
        }
    }

}