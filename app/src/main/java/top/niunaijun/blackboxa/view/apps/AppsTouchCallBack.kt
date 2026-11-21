package top.niunaijun.blackboxa.view.apps

import android.util.Log
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class AppsTouchCallBack(private val onMoveBlock: (from: Int, to: Int) -> Unit) :
    ItemTouchHelper.Callback() {

    companion object {
        private const val TAG = "AppsTouchCallBack"
    }

    override fun isLongPressDragEnabled(): Boolean = false

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        return try {
            makeMovementFlags(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN or
                    ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
                0
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting movement flags: ${e.message}")
            makeMovementFlags(0, 0)
        }
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        return try {
            val fromPosition = viewHolder.bindingAdapterPosition
            val toPosition = target.bindingAdapterPosition
            if (fromPosition == RecyclerView.NO_POSITION ||
                toPosition == RecyclerView.NO_POSITION ||
                fromPosition == toPosition
            ) {
                false
            } else {
                onMoveBlock(fromPosition, toPosition)
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onMove: ${e.message}")
            false
        }
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
    }
}
