package dev.veeso.biangbiangui.config

/** App-level branding metadata. Mirrors iOS `Branding`. */
data class Branding(
    val appName: String,
    val accentColorHex: String,
    val logoAssetName: String,
    val buttonLogoAssetName: String,
    /** e.g. "veeso/BiangBiang-Hanzi" */
    val githubRepo: String,
    val supportEmail: String,
    val appStoreId: String,
    val playStoreId: String,
)
