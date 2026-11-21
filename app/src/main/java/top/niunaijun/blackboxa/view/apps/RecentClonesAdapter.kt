package top.niunaijun.blackboxa.view.apps

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.app.AppManager
import top.niunaijun.blackboxa.bean.AppInfo
import top.niunaijun.blackboxa.databinding.ItemRecentCloneBinding

class RecentClonesAdapter(
    private val onOpenClick: (AppInfo) -> Unit,
    private val onQuickCloneClick: (AppInfo) -> Unit,
    private val onLongClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<RecentClonesAdapter.VH>() {

    private var items: List<AppInfo> = emptyList()

    fun submitList(newItems: List<AppInfo>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemRecentCloneBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class VH(private val binding: ItemRecentCloneBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var boundItem: AppInfo? = null

        init {
            binding.root.setOnClickListener {
                boundItem?.let(onOpenClick)
            }
            binding.root.setOnLongClickListener {
                boundItem?.let(onLongClick)
                true
            }
            binding.btnRecentQuickClone.setOnClickListener {
                boundItem?.let(onQuickCloneClick)
            }
        }

        fun bind(item: AppInfo) {
            boundItem = item
            val ctx = binding.root.context
            binding.ivRecentIcon.setImageDrawable(
                item.icon ?: ContextCompat.getDrawable(ctx, R.drawable.ic_brand_logo)
            )
            binding.tvRecentName.text = item.name.ifBlank { item.packageName }
            binding.tvRecentMeta.text = ctx.getString(
                R.string.recent_clone_subtitle,
                item.cloneIndex,
                AppManager.getSpaceDisplayName(item.userId)
            )
        }
    }
}
