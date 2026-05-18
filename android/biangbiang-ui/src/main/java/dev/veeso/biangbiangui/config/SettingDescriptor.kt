package dev.veeso.biangbiangui.config

/**
 * Declarative descriptor for an app-supplied extra setting.
 * Mirrors iOS `SettingDescriptor`.
 */
data class SettingDescriptor(
    val key: String,
    val kind: Kind,
    val label: String,
    /** "true"/"false" for toggles; option for pickers. */
    val defaultValue: String,
    val footer: String? = null,
) {
    /** Stable identity, mirrors iOS `Identifiable.id`. */
    val id: String get() = key

    /** Mirrors iOS `SettingDescriptor.Kind`. */
    sealed class Kind {
        data object Toggle : Kind()

        data class Picker(val options: List<String>) : Kind()
    }
}
