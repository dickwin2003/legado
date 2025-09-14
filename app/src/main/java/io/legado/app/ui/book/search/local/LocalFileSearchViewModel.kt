package io.legado.app.ui.book.search.local

import android.app.Application
import android.os.Environment
import androidx.lifecycle.MutableLiveData
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppPattern
import io.legado.app.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/**
 * 本地文件搜索ViewModel
 */
class LocalFileSearchViewModel(application: Application) : BaseViewModel(application) {

    val searchResultLiveData = MutableLiveData<List<LocalBookFile>>()
    val searchProgressLiveData = MutableLiveData<String>()
    
    private val supportedExtensions = arrayOf("txt", "epub", "umd", "pdf", "mobi", "azw3", "azw", "zip", "rar", "7z")
    
    data class LocalBookFile(
        val file: File,
        val name: String,
        val path: String,
        val size: Long,
        val lastModified: Long,
        val extension: String,
        val isArchive: Boolean = false
    )

    /**
     * 搜索本地文件
     */
    fun searchLocalFiles(keyword: String) {
        execute {
            val results = mutableListOf<LocalBookFile>()
            searchProgressLiveData.postValue("开始搜索...")
            
            // 搜索内部存储
            searchInDirectory(Environment.getExternalStorageDirectory(), keyword, results)
            
            // 搜索其他可能的存储位置
            val otherStorages = getExternalStorageDirectories()
            otherStorages.forEach { storage ->
                if (storage.exists() && storage.canRead()) {
                    searchInDirectory(storage, keyword, results)
                }
            }
            
            searchResultLiveData.postValue(results)
            searchProgressLiveData.postValue("搜索完成，找到 ${results.size} 个文件")
        }.onError {
            searchProgressLiveData.postValue("搜索出错: ${it.localizedMessage}")
        }
    }

    /**
     * 在指定目录中搜索文件
     */
    private fun searchInDirectory(directory: File, keyword: String, results: MutableList<LocalBookFile>) {
        try {
            if (!directory.exists() || !directory.canRead()) return
            
            searchProgressLiveData.postValue("搜索: ${directory.name}")
            
            directory.listFiles()?.forEach { file ->
                try {
                    when {
                        file.isDirectory -> {
                            // 递归搜索子目录
                            if (!file.name.startsWith(".") && file.canRead()) {
                                searchInDirectory(file, keyword, results)
                            }
                        }
                        file.isFile -> {
                            val extension = FileUtils.getExtension(file.name).lowercase()
                            if (supportedExtensions.contains(extension)) {
                                // 检查文件名是否包含关键词，或者没有关键词时显示所有支持的文件
                                if (keyword.isBlank() || file.name.contains(keyword, ignoreCase = true)) {
                                    val isArchive = file.name.matches(AppPattern.archiveFileRegex)
                                    results.add(
                                        LocalBookFile(
                                            file = file,
                                            name = file.name,
                                            path = file.absolutePath,
                                            size = file.length(),
                                            lastModified = file.lastModified(),
                                            extension = extension,
                                            isArchive = isArchive
                                        )
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // 跳过无权限访问的文件
                }
            }
        } catch (e: Exception) {
            // 跳过无权限访问的目录
        }
    }

    /**
     * 获取所有外部存储目录
     */
    private fun getExternalStorageDirectories(): List<File> {
        val directories = mutableListOf<File>()
        
        // 添加标准外部存储
        directories.add(Environment.getExternalStorageDirectory())
        
        // 尝试添加其他可能的存储位置
        try {
            val potentialPaths = arrayOf(
                "/storage",
                "/mnt", 
                "/sdcard",
                "/storage/emulated/0",
                "/storage/sdcard0",
                "/storage/sdcard1"
            )
            
            potentialPaths.forEach { path ->
                val dir = File(path)
                if (dir.exists() && dir.isDirectory && dir.canRead()) {
                    directories.add(dir)
                    // 查找子存储设备
                    dir.listFiles()?.forEach { subDir ->
                        if (subDir.isDirectory && subDir.canRead() && 
                            !subDir.name.startsWith(".") &&
                            subDir.name != "Android") {
                            directories.add(subDir)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // 忽略权限错误
        }
        
        return directories.distinctBy { it.absolutePath }
    }

    /**
     * 按文件类型过滤搜索流
     */
    fun searchFlow(keyword: String, fileType: String? = null): Flow<List<LocalBookFile>> = flow {
        val results = mutableListOf<LocalBookFile>()
        searchProgressLiveData.postValue("开始搜索...")
        
        // 搜索内部存储
        searchInDirectoryFlow(Environment.getExternalStorageDirectory(), keyword, fileType, results)
        emit(results.toList())
        
        // 搜索其他存储位置
        val otherStorages = getExternalStorageDirectories()
        otherStorages.forEach { storage ->
            if (storage.exists() && storage.canRead()) {
                searchInDirectoryFlow(storage, keyword, fileType, results)
                emit(results.toList())
            }
        }
        
        searchProgressLiveData.postValue("搜索完成，找到 ${results.size} 个文件")
    }.flowOn(Dispatchers.IO)

    private fun searchInDirectoryFlow(
        directory: File, 
        keyword: String, 
        fileType: String?, 
        results: MutableList<LocalBookFile>
    ) {
        try {
            if (!directory.exists() || !directory.canRead()) return
            
            searchProgressLiveData.postValue("搜索: ${directory.name}")
            
            directory.listFiles()?.forEach { file ->
                try {
                    when {
                        file.isDirectory -> {
                            if (!file.name.startsWith(".") && file.canRead()) {
                                searchInDirectoryFlow(file, keyword, fileType, results)
                            }
                        }
                        file.isFile -> {
                            val extension = FileUtils.getExtension(file.name).lowercase()
                            val shouldInclude = when {
                                fileType != null -> extension == fileType.lowercase()
                                else -> supportedExtensions.contains(extension)
                            }
                            
                            if (shouldInclude) {
                                if (keyword.isBlank() || file.name.contains(keyword, ignoreCase = true)) {
                                    val isArchive = file.name.matches(AppPattern.archiveFileRegex)
                                    results.add(
                                        LocalBookFile(
                                            file = file,
                                            name = file.name,
                                            path = file.absolutePath,
                                            size = file.length(),
                                            lastModified = file.lastModified(),
                                            extension = extension,
                                            isArchive = isArchive
                                        )
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // 跳过无权限访问的文件
                }
            }
        } catch (e: Exception) {
            // 跳过无权限访问的目录
        }
    }
}