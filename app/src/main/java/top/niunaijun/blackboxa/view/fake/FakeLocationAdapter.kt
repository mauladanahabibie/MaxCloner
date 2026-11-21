package top.niunaijun.blackboxa.view.fake

import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import cbfg.rvadapter.RVHolder
import cbfg.rvadapter.RVHolderFactory
import java.util.Locale
import top.niunaijun.blackbox.fake.frameworks.BLocationManager
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.bean.FakeLocationBean
import top.niunaijun.blackboxa.databinding.ItemFakeBinding

class FakeLocationAdapter : RVHolderFactory() {

    override fun createViewHolder(parent: ViewGroup?, viewType: Int, item: Any): RVHolder<out Any> {
        return FakeLocationVH(inflate(R.layout.item_fake, parent))
    }

    class FakeLocationVH(itemView: View) : RVHolder<FakeLocationBean>(itemView) {

        private val binding = ItemFakeBinding.bind(itemView)

        override fun setContent(item: FakeLocationBean, isSelected: Boolean, payload: Any?) {
            val ctx = itemView.context
            binding.icon.setImageDrawable(item.icon)
            binding.name.text = item.name
            binding.packageName.text = item.packageName

            val location = item.fakeLocation
            if (location == null || item.fakeLocationPattern == BLocationManager.CLOSE_MODE) {
                binding.fakeLocation.setText(R.string.fake_location_inactive_pill)
                binding.fakeLocation.setBackgroundResource(R.drawable.bg_pill_muted)
                binding.fakeLocation.setTextColor(ContextCompat.getColor(ctx, R.color.mc_text_secondary))
            } else {
                binding.fakeLocation.text = String.format(
                    Locale.US,
                    "%.3f, %.3f",
                    location.latitude,
                    location.longitude
                )
                binding.fakeLocation.setBackgroundResource(R.drawable.bg_pill_warning)
                binding.fakeLocation.setTextColor(
                    ContextCompat.getColor(ctx, R.color.mc_on_warning_container)
                )
            }
        }
    }
}
