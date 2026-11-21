package top.niunaijun.blackboxa.data

import android.content.pm.ApplicationInfo

class AppsSortComparator(private val sortedList: List<String>) : Comparator<ApplicationInfo> {
    override fun compare(o1: ApplicationInfo?, o2: ApplicationInfo?): Int {
        if (o1 == null || o2 == null) {
            return 0
        }

        val firstRaw = sortedList.indexOf(o1.packageName)
        val secondRaw = sortedList.indexOf(o2.packageName)
        val first = if (firstRaw == -1) Int.MAX_VALUE else firstRaw
        val second = if (secondRaw == -1) Int.MAX_VALUE else secondRaw
        return first.compareTo(second)
    }
}
