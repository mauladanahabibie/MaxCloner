package top.niunaijun.blackboxa.util

import org.junit.Assert.assertEquals
import org.junit.Test
import top.niunaijun.blackboxa.data.AppsRepository

class RecentClonesAndSlotTest {

    @Test
    fun recentClonesStore_addsDeduplicatesAndCaps() {
        var entries = emptyList<Pair<Int, String>>()
        entries = RecentClonesStore.addEntry(entries, 0, "com.muse", maxSize = 3)
        entries = RecentClonesStore.addEntry(entries, 1, "com.muse", maxSize = 3)
        entries = RecentClonesStore.addEntry(entries, 0, "com.whatsapp", maxSize = 3)
        // Re-adding (0, "com.muse") moves it to front without duplicating
        entries = RecentClonesStore.addEntry(entries, 0, "com.muse", maxSize = 3)
        assertEquals(
            listOf(0 to "com.muse", 0 to "com.whatsapp", 1 to "com.muse"),
            entries
        )

        // Adding a 4th item caps list at maxSize = 3
        entries = RecentClonesStore.addEntry(entries, 2, "com.telegram", maxSize = 3)
        assertEquals(
            listOf(2 to "com.telegram", 0 to "com.muse", 0 to "com.whatsapp"),
            entries
        )
    }

    @Test
    fun recentClonesStore_roundTripsAndRemoves() {
        val original = listOf(2 to "com.muse", 0 to "com.whatsapp")
        val raw = RecentClonesStore.serializeEntries(original)
        assertEquals(original, RecentClonesStore.parseEntries(raw))

        val removed = RecentClonesStore.removeEntry(original, 2, "com.muse")
        assertEquals(listOf(0 to "com.whatsapp"), removed)
    }

    @Test
    fun resolveNextFreeUserId_allocatesWithoutManualSpaceSwitching() {
        // From All (-1), already cloned in 0 and 1 -> allocates 2
        assertEquals(
            2,
            AppsRepository.resolveNextFreeUserId(
                installedUserIdsForPkg = setOf(0, 1),
                preferredUserId = AppsRepository.ALL_USERS_ID,
                maxExistingUserId = 1
            )
        )

        // From All (-1) with gap {0, 2} -> fills 1
        assertEquals(
            1,
            AppsRepository.resolveNextFreeUserId(
                installedUserIdsForPkg = setOf(0, 2),
                preferredUserId = AppsRepository.ALL_USERS_ID,
                maxExistingUserId = 2
            )
        )

        // From Space 1 (0) when 0 already has the app -> auto-allocates 1 instead of overwriting
        assertEquals(
            1,
            AppsRepository.resolveNextFreeUserId(
                installedUserIdsForPkg = setOf(0),
                preferredUserId = 0,
                maxExistingUserId = 0
            )
        )

        // From Space 2 (1) when 1 does not have the app -> keeps preferred 1
        assertEquals(
            1,
            AppsRepository.resolveNextFreeUserId(
                installedUserIdsForPkg = setOf(0),
                preferredUserId = 1,
                maxExistingUserId = 1
            )
        )
    }
}
