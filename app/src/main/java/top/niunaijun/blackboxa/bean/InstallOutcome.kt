package top.niunaijun.blackboxa.bean

data class InstallOutcome(
    val success: Boolean,
    val message: String,
    val packageName: String? = null,
    val userId: Int = 0,
    val isInstallAction: Boolean = false
)
