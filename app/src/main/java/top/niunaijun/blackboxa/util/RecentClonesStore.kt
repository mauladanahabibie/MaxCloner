package top.niunaijun.blackboxa.util

import android.content.Context
import androidx.core.content.edit

object RecentClonesStore {

    private const val PREF_NAME = "RecentClones"
    private const val KEY_ENTRIES = "entries"
    private const val DEFAULT_MAX_SIZE = 10

    fun parseEntries(raw: String?): List<Pair<Int, String>> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(",")
            .mapNotNull { token ->
                val trimmed = token.trim()
                val idx = trimmed.indexOf(':')
                if (idx <= 0 || idx >= trimmed.lastIndex) return@mapNotNull null
                val userId = trimmed.substring(0, idx).toIntOrNull()?.takeIf { it >= 0 }
                    ?: return@mapNotNull null
                val pkg = trimmed.substring(idx + 1).trim().takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                userId to pkg
            }
    }

    fun serializeEntries(entries: List<Pair<Int, String>>): String {
        return entries.joinToString(",") { (userId, pkg) -> "$userId:$pkg" }
    }

    fun addEntry(
        existing: List<Pair<Int, String>>,
        userId: Int,
        packageName: String,
        maxSize: Int = DEFAULT_MAX_SIZE
    ): List<Pair<Int, String>> {
        if (userId < 0 || packageName.isBlank()) return existing
        val updated = ArrayList<Pair<Int, String>>(minOf(existing.size + 1, maxSize))
        updated.add(userId to packageName)
        for (entry in existing) {
            if (entry.first == userId && entry.second == packageName) continue
            if (updated.size >= maxSize) break
            updated.add(entry)
        }
        return updated
    }

    fun removeEntry(
        existing: List<Pair<Int, String>>,
        userId: Int,
        packageName: String
    ): List<Pair<Int, String>> {
        return existing.filterNot { it.first == userId && it.second == packageName }
    }

    fun recordClone(context: Context, userId: Int, packageName: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val current = parseEntries(prefs.getString(KEY_ENTRIES, null))
        val updated = addEntry(current, userId, packageName)
        prefs.edit { putString(KEY_ENTRIES, serializeEntries(updated)) }
    }

    fun removeClone(context: Context, userId: Int, packageName: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val current = parseEntries(prefs.getString(KEY_ENTRIES, null))
        val updated = removeEntry(current, userId, packageName)
        prefs.edit { putString(KEY_ENTRIES, serializeEntries(updated)) }
    }

    fun getRecentEntries(context: Context): List<Pair<Int, String>> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return parseEntries(prefs.getString(KEY_ENTRIES, null))
    }
}
