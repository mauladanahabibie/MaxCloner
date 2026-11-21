package top.niunaijun.blackboxa.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import top.niunaijun.blackboxa.view.main.ShortcutActivity

class ShortcutUtilTest {

    @Test
    fun buildShortcutId_isStablePerUserAndPackage() {
        assertEquals(
            "clone_0_com.whatsapp",
            ShortcutUtil.buildShortcutId(0, "com.whatsapp")
        )
        assertEquals(
            "clone_2_com.whatsapp",
            ShortcutUtil.buildShortcutId(2, "com.whatsapp")
        )
    }

    @Test
    fun defaultShortcutLabel_formatsCleanlyAcrossSpaces() {
        assertEquals("WhatsApp", ShortcutUtil.defaultShortcutLabel("WhatsApp", 0, null))
        assertEquals("WhatsApp #2", ShortcutUtil.defaultShortcutLabel("WhatsApp", 1, null))
        assertEquals("WhatsApp (Work)", ShortcutUtil.defaultShortcutLabel("WhatsApp", 1, "Work"))
        assertEquals("WhatsApp (Toko)", ShortcutUtil.defaultShortcutLabel("WhatsApp", 0, "  Toko  "))
    }

    @Test
    fun parseLaunchTarget_readsExtrasFirst() {
        val result = ShortcutActivity.parseLaunchTarget(
            pkgExtra = "com.example.app",
            userIdExtra = 2,
            dataUriString = "maxcloner://launch/0/com.other"
        )
        assertEquals("com.example.app" to 2, result)
    }

    @Test
    fun parseLaunchTarget_fallsBackToLaunchUri() {
        val result = ShortcutActivity.parseLaunchTarget(
            pkgExtra = null,
            userIdExtra = null,
            dataUriString = "maxcloner://launch/3/com.instagram.android"
        )
        assertEquals("com.instagram.android" to 3, result)
    }

    @Test
    fun parseLaunchTarget_rejectsInvalidUri() {
        assertNull(ShortcutActivity.parseLaunchTarget(null, null, null))
        assertNull(ShortcutActivity.parseLaunchTarget(null, null, "https://example.com"))
        assertNull(ShortcutActivity.parseLaunchTarget(null, null, "maxcloner://launch/-1/com.app"))
        assertNull(ShortcutActivity.parseLaunchTarget(null, null, "maxcloner://launch/abc/com.app"))
    }
}
