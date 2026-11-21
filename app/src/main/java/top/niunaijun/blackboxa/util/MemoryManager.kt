package top.niunaijun.blackboxa.util

import android.util.Log

object MemoryManager {

    private const val TAG = "MemoryManager"
    private const val MEMORY_THRESHOLD = 0.85
    private const val CRITICAL_MEMORY_THRESHOLD = 0.94

    fun isMemorySafe(): Boolean {
        return try {
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val maxMemory = runtime.maxMemory()
            val memoryUsage = usedMemory.toDouble() / maxMemory.toDouble()
            memoryUsage < MEMORY_THRESHOLD
        } catch (e: Exception) {
            Log.e(TAG, "Error checking memory: ${e.message}")
            true
        }
    }

    fun isMemoryCritical(): Boolean {
        return try {
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val maxMemory = runtime.maxMemory()
            val memoryUsage = usedMemory.toDouble() / maxMemory.toDouble()
            memoryUsage > CRITICAL_MEMORY_THRESHOLD
        } catch (e: Exception) {
            Log.e(TAG, "Error checking critical memory: ${e.message}")
            false
        }
    }

    fun getMemoryUsagePercentage(): Int {
        return try {
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val maxMemory = runtime.maxMemory()
            val memoryUsage = usedMemory.toDouble() / maxMemory.toDouble()
            (memoryUsage * 100).toInt()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting memory usage: ${e.message}")
            0
        }
    }

    fun forceGarbageCollectionIfNeeded(): Boolean {
        return try {
            if (isMemoryCritical()) {
                Log.w(TAG, "Memory usage critical (${getMemoryUsagePercentage()}%), requesting GC")
                System.gc()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during garbage collection: ${e.message}")
            false
        }
    }

    fun optimizeMemoryForRecyclerView() {
        try {
            val memoryUsage = getMemoryUsagePercentage()
            if (memoryUsage > 85) {
                Log.d(TAG, "Memory usage elevated (${memoryUsage}%), requesting GC")
                Runtime.getRuntime().gc()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error optimizing memory: ${e.message}")
        }
    }

    fun shouldSkipIconLoading(): Boolean {
        return try {
            getMemoryUsagePercentage() > 92
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if should skip icon loading: ${e.message}")
            false
        }
    }

    fun getMemoryInfo(): String {
        return try {
            val runtime = Runtime.getRuntime()
            val totalMemory = runtime.totalMemory()
            val freeMemory = runtime.freeMemory()
            val usedMemory = totalMemory - freeMemory
            val maxMemory = runtime.maxMemory()
            "Memory: ${usedMemory / 1024 / 1024}MB used / ${maxMemory / 1024 / 1024}MB max (${getMemoryUsagePercentage()}%)"
        } catch (e: Exception) {
            "Memory: Unknown (${e.message})"
        }
    }
}
