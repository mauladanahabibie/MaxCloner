package top.niunaijun.blackboxa.view.apps

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import cbfg.rvadapter.RVHolder
import cbfg.rvadapter.RVHolderFactory
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.bean.AppInfo
import top.niunaijun.blackboxa.databinding.ItemAppBinding
import top.niunaijun.blackboxa.util.ShortcutUtil

class AppsAdapter(
    private val userId: Int,
    private val onMoreClick: (AppInfo) -> Unit
) : RVHolderFactory() {

    companion object {
        private const val TAG = "AppsAdapter"
        private const val MAX_ICON_SIZE = 144
    }

    override fun createViewHolder(parent: ViewGroup?, viewType: Int, item: Any): RVHolder<out Any> {
        return AppsVH(inflate(R.layout.item_app, parent), userId, onMoreClick)
    }

    class AppsVH(
        itemView: View,
        private val userId: Int,
        private val onMoreClick: (AppInfo) -> Unit
    ) : RVHolder<AppInfo>(itemView) {
        val binding = ItemAppBinding.bind(itemView)
        private var currentItem: AppInfo? = null

        init {
            binding.icon.scaleType = ImageView.ScaleType.FIT_CENTER
            binding.ivMore.setOnClickListener {
                currentItem?.let(onMoreClick)
            }
        }

        override fun setContent(item: AppInfo, isSelected: Boolean, payload: Any?) {
            currentItem = item
            try {
                setIconSafely(item.icon, item.packageName)
                binding.name.text = item.name.ifBlank { item.packageName }
                binding.cornerLabel.visibility = if (item.isXpModule) View.VISIBLE else View.INVISIBLE
                val isPinned = ShortcutUtil.isShortcutPinned(
                    itemView.context,
                    userId,
                    item.packageName
                )
                binding.ivPinnedBadge.visibility = if (isPinned) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                Log.e(TAG, "Error setting content for ${item.packageName}: ${e.message}")
                setSafeDefaults()
            }
        }

        private fun setIconSafely(icon: Drawable?, packageName: String) {
            try {
                if (icon != null) {
                    binding.icon.setImageDrawable(optimizeIcon(icon))
                } else {
                    binding.icon.setImageResource(R.drawable.ic_brand_logo)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set icon for $packageName: ${e.message}")
                binding.icon.setImageResource(R.drawable.ic_brand_logo)
            }
        }

        private fun optimizeIcon(icon: Drawable): Drawable {
            return try {
                if (icon is BitmapDrawable) {
                    val bitmap = icon.bitmap
                    if (bitmap != null && (bitmap.width > MAX_ICON_SIZE || bitmap.height > MAX_ICON_SIZE)) {
                        val scaledBitmap = Bitmap.createScaledBitmap(
                            bitmap,
                            MAX_ICON_SIZE,
                            MAX_ICON_SIZE,
                            true
                        )
                        BitmapDrawable(itemView.resources, scaledBitmap)
                    } else {
                        icon
                    }
                } else {
                    icon
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error optimizing icon: ${e.message}")
                icon
            }
        }

        private fun setSafeDefaults() {
            binding.icon.setImageDrawable(
                ContextCompat.getDrawable(itemView.context, R.drawable.ic_brand_logo)
            )
            binding.name.text = itemView.context.getString(R.string.app_name)
            binding.cornerLabel.visibility = View.INVISIBLE
            binding.ivPinnedBadge.visibility = View.GONE
        }
    }
}
