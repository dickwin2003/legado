package io.legado.app.ui.main.explore

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.view.children
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.databinding.ItemFindBookBinding
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.source.clearExploreKindsCache
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.utils.activity
import io.legado.app.utils.dpToPx
import io.legado.app.utils.gone
import io.legado.app.utils.removeLastElement
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.visible
import kotlinx.coroutines.CoroutineScope
import splitties.views.onLongClick
import io.legado.app.constant.AppLog

class ExploreAdapter(context: Context, val callBack: CallBack) :
    RecyclerAdapter<BookSourcePart, ItemFindBookBinding>(context) {

    private var exIndex = -1
    private var scrollTo = -1

    override fun getViewBinding(parent: ViewGroup): ItemFindBookBinding {
        return ItemFindBookBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemFindBookBinding,
        item: BookSourcePart,
        payloads: MutableList<Any>
    ) {
        binding.apply {
            tvName.text = item.bookSourceName
            // 参考RSS适配器，使用Glide加载书源图标
            try {
                val options = com.bumptech.glide.request.RequestOptions()
                    .set(io.legado.app.help.glide.OkHttpModelLoader.sourceOriginOption, item.bookSourceUrl)
                io.legado.app.help.glide.ImageLoader.load(context, item.bookSourceUrl + "/favicon.ico")
                    .apply(options)
                    .centerCrop()
                    .placeholder(R.drawable.image_legado)
                    .error(R.drawable.image_legado)
                    .into(ivIcon)
            } catch (e: Exception) {
                // 如果加载失败，使用默认图标
                ivIcon.setImageResource(R.drawable.image_legado)
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemFindBookBinding) {
        binding.apply {
            // 参考RSS适配器的实现，点击打开书源
            root.setOnClickListener {
                getItem(holder.layoutPosition)?.let { item ->
                    AppLog.put("ExploreAdapter - 点击书源: ${item.bookSourceName}, URL: ${item.bookSourceUrl}")
                    callBack.openBookSource(item)
                }
            }
            root.onLongClick {
                getItem(holder.layoutPosition)?.let { item ->
                    AppLog.put("ExploreAdapter - 长按书源: ${item.bookSourceName}")
                    showMenu(root, holder.layoutPosition)
                }
            }
        }
    }

    private fun showMenu(view: View, position: Int): Boolean {
        val source = getItem(position) ?: return true
        val popupMenu = PopupMenu(context, view)
        popupMenu.inflate(R.menu.explore_item)
        popupMenu.menu.findItem(R.id.menu_login).isVisible = source.hasLoginUrl
        popupMenu.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_edit -> callBack.editSource(source.bookSourceUrl)
                R.id.menu_top -> callBack.toTop(source)
                R.id.menu_search -> callBack.searchBook(source)
                R.id.menu_login -> context.startActivity<SourceLoginActivity> {
                    putExtra("type", "bookSource")
                    putExtra("key", source.bookSourceUrl)
                }

                R.id.menu_refresh -> Coroutine.async(callBack.scope) {
                    source.clearExploreKindsCache()
                }.onSuccess {
                    notifyItemChanged(position)
                }

                R.id.menu_open_website -> callBack.openWebsite(source.bookSourceUrl)
                R.id.menu_del -> callBack.deleteSource(source)
                R.id.menu_disable -> callBack.disableSource(source)
            }
            true
        }
        popupMenu.show()
        return true
    }

    interface CallBack {
        val scope: CoroutineScope
        fun scrollTo(pos: Int)
        fun openExplore(sourceUrl: String, title: String, exploreUrl: String?)
        fun editSource(sourceUrl: String)
        fun toTop(source: BookSourcePart)
        fun deleteSource(source: BookSourcePart)
        fun searchBook(bookSource: BookSourcePart)
        fun openWebsite(sourceUrl: String)
        fun openBookSource(bookSource: BookSourcePart)
        fun disableSource(bookSource: BookSourcePart)
    }
}
