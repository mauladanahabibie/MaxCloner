package top.niunaijun.blackboxa.view.list

import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import cbfg.rvadapter.RVHolder
import cbfg.rvadapter.RVHolderFactory
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.bean.InstalledAppBean
import top.niunaijun.blackboxa.databinding.ItemPackageBinding

class ListAdapter : RVHolderFactory() {

    override fun createViewHolder(parent: ViewGroup?, viewType: Int, item: Any): RVHolder<out Any> {
        return ListVH(inflate(R.layout.item_package, parent))
    }

    class ListVH(itemView: View) : RVHolder<InstalledAppBean>(itemView) {

        val binding = ItemPackageBinding.bind(itemView)

        override fun setContent(item: InstalledAppBean, isSelected: Boolean, payload: Any?) {
            val ctx = itemView.context
            binding.icon.setImageDrawable(
                item.icon ?: ContextCompat.getDrawable(ctx, R.drawable.ic_brand_logo)
            )
            binding.name.text = item.name
            binding.packageName.text = item.packageName

            if (item.isInstall) {
                binding.tvStatusBadge.setText(R.string.cloned_badge)
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_pill_success)
                binding.tvStatusBadge.setTextColor(
                    ContextCompat.getColor(ctx, R.color.mc_on_success_container)
                )
            } else {
                binding.tvStatusBadge.setText(R.string.clone_action)
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_pill_primary)
                binding.tvStatusBadge.setTextColor(
                    ContextCompat.getColor(ctx, R.color.mc_on_primary_container)
                )
            }
        }
    }
}
