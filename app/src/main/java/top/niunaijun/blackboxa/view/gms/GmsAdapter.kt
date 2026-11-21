package top.niunaijun.blackboxa.view.gms

import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import cbfg.rvadapter.RVHolder
import cbfg.rvadapter.RVHolderFactory
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.bean.GmsBean
import top.niunaijun.blackboxa.databinding.ItemGmsBinding

class GmsAdapter : RVHolderFactory() {

    override fun createViewHolder(parent: ViewGroup?, viewType: Int, item: Any): RVHolder<out Any> {
        return GmsVH(inflate(R.layout.item_gms, parent))
    }

    class GmsVH(itemView: View) : RVHolder<GmsBean>(itemView) {

        private val binding = ItemGmsBinding.bind(itemView)

        override fun setContent(item: GmsBean, isSelected: Boolean, payload: Any?) {
            val ctx = itemView.context
            binding.tvTitle.text = item.userName

            if (item.isInstalledGms) {
                binding.tvStatus.setText(R.string.gms_active)
                binding.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.mc_success))
            } else {
                binding.tvStatus.setText(R.string.gms_inactive)
                binding.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.mc_text_secondary))
            }

            binding.checkbox.setOnCheckedChangeListener(null)
            binding.checkbox.isChecked = item.isInstalledGms
            binding.checkbox.setOnCheckedChangeListener { buttonView, _ ->
                if (buttonView.isPressed) {
                    binding.root.performClick()
                }
            }
        }
    }
}
