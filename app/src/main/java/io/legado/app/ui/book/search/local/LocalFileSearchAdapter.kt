package io.legado.app.ui.book.search.local

import android.content.Context
import android.text.format.Formatter
import android.view.ViewGroup
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.ItemLocalFileSearchBinding
import io.legado.app.utils.ConvertUtils
import java.text.SimpleDateFormat
import java.util.*

/**
 * 本地文件搜索适配器
 */
class LocalFileSearchAdapter(context: Context, private val callBack: CallBack) :
    RecyclerAdapter<LocalFileSearchViewModel.LocalBookFile, ItemLocalFileSearchBinding>(context) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    interface CallBack {
        fun openFile(file: LocalFileSearchViewModel.LocalBookFile)
        fun addToBookShelf(file: LocalFileSearchViewModel.LocalBookFile)
    }

    override fun getViewBinding(parent: ViewGroup): ItemLocalFileSearchBinding {
        return ItemLocalFileSearchBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemLocalFileSearchBinding,
        item: LocalFileSearchViewModel.LocalBookFile,
        payloads: MutableList<Any>
    ) {
        binding.apply {
            tvFileName.text = item.name
            tvFilePath.text = item.path
            tvFileSize.text = Formatter.formatFileSize(context, item.size)
            tvFileTime.text = dateFormat.format(Date(item.lastModified))
            tvFileExtension.text = item.extension.uppercase()
            
            // 设置文件类型图标背景色
            val color = when (item.extension.lowercase()) {
                "txt" -> android.graphics.Color.parseColor("#4CAF50")
                "epub" -> android.graphics.Color.parseColor("#2196F3")
                "pdf" -> android.graphics.Color.parseColor("#F44336")
                "mobi", "azw3", "azw" -> android.graphics.Color.parseColor("#FF9800")
                "umd" -> android.graphics.Color.parseColor("#9C27B0")
                "zip", "rar", "7z" -> android.graphics.Color.parseColor("#607D8B")
                else -> android.graphics.Color.parseColor("#9E9E9E")
            }
            tvFileExtension.setBackgroundColor(color)
            
            // 如果是压缩文件，显示特殊标识
            if (item.isArchive) {
                tvArchiveFlag.visibility = android.view.View.VISIBLE
                tvArchiveFlag.text = "压缩包"
            } else {
                tvArchiveFlag.visibility = android.view.View.GONE
            }
            
            root.setOnClickListener {
                callBack.openFile(item)
            }
            
            btnAddToShelf.setOnClickListener {
                callBack.addToBookShelf(item)
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemLocalFileSearchBinding) {
        // 已在convert中处理点击事件
    }
}