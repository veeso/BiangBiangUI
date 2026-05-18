package dev.veeso.biangbiangui.config

/**
 * User-facing string table with English defaults.
 * Mirrors iOS `UIStrings` (plan casing: `UiStrings`).
 */
data class UiStrings(
    val inputTitle: String = "Text",
    val outputTitle: String = "Transliteration",
    val translationTitle: String = "Translation",
    val paste: String = "Paste",
    val copy: String = "Copy",
    val listen: String = "Listen",
    val stop: String = "Stop",
    val save: String = "Save",
    val savedToHistory: String = "Saved to History",
    val textCopied: String = "Text copied",
    val clearAll: String = "Clear All",
    val clearAllConfirm: String = "Delete all history?",
    val original: String = "Original",
    val transliterated: String = "Transliterated",
    val historyEmpty: String = "No history yet",
    val cameraDisabledTitle: String = "Camera access disabled",
    val cameraDisabledMessage: String = "Enable camera access in Settings to scan text.",
    val openSettings: String = "Open Settings",
    val tapToCopyLongPressToSave: String = "Tap to copy · long-press to save",
    val translate: String = "Translate",
    val reportBug: String = "Report a bug",
    val openGithubIssues: String = "Open GitHub Issues",
    val sendEmail: String = "Send Email",
    val rateTitle: String = "Enjoying the app?",
    val rateMessage: String = "A quick rating really helps.",
    val rateNow: String = "Rate now",
    val notNow: String = "Not now",
    val dontAskAgain: String = "Don't ask again",
    val translationLanguage: String = "Translation language",
    val tabText: String = "Text",
    val tabCamera: String = "Camera",
    val tabHistory: String = "History",
    val tabSettings: String = "Settings",
) {
    companion object {
        /** Merge override values keyed by property name over the English defaults. */
        fun merged(overrides: Map<String, String>?): UiStrings {
            if (overrides.isNullOrEmpty()) return UiStrings()
            val d = UiStrings() // single source of truth for defaults
            return UiStrings(
                inputTitle = overrides["inputTitle"] ?: d.inputTitle,
                outputTitle = overrides["outputTitle"] ?: d.outputTitle,
                translationTitle = overrides["translationTitle"] ?: d.translationTitle,
                paste = overrides["paste"] ?: d.paste,
                copy = overrides["copy"] ?: d.copy,
                listen = overrides["listen"] ?: d.listen,
                stop = overrides["stop"] ?: d.stop,
                save = overrides["save"] ?: d.save,
                savedToHistory = overrides["savedToHistory"] ?: d.savedToHistory,
                textCopied = overrides["textCopied"] ?: d.textCopied,
                clearAll = overrides["clearAll"] ?: d.clearAll,
                clearAllConfirm = overrides["clearAllConfirm"] ?: d.clearAllConfirm,
                original = overrides["original"] ?: d.original,
                transliterated = overrides["transliterated"] ?: d.transliterated,
                historyEmpty = overrides["historyEmpty"] ?: d.historyEmpty,
                cameraDisabledTitle = overrides["cameraDisabledTitle"] ?: d.cameraDisabledTitle,
                cameraDisabledMessage = overrides["cameraDisabledMessage"]
                    ?: d.cameraDisabledMessage,
                openSettings = overrides["openSettings"] ?: d.openSettings,
                tapToCopyLongPressToSave = overrides["tapToCopyLongPressToSave"]
                    ?: d.tapToCopyLongPressToSave,
                translate = overrides["translate"] ?: d.translate,
                reportBug = overrides["reportBug"] ?: d.reportBug,
                openGithubIssues = overrides["openGithubIssues"] ?: d.openGithubIssues,
                sendEmail = overrides["sendEmail"] ?: d.sendEmail,
                rateTitle = overrides["rateTitle"] ?: d.rateTitle,
                rateMessage = overrides["rateMessage"] ?: d.rateMessage,
                rateNow = overrides["rateNow"] ?: d.rateNow,
                notNow = overrides["notNow"] ?: d.notNow,
                dontAskAgain = overrides["dontAskAgain"] ?: d.dontAskAgain,
                translationLanguage = overrides["translationLanguage"] ?: d.translationLanguage,
                tabText = overrides["tabText"] ?: d.tabText,
                tabCamera = overrides["tabCamera"] ?: d.tabCamera,
                tabHistory = overrides["tabHistory"] ?: d.tabHistory,
                tabSettings = overrides["tabSettings"] ?: d.tabSettings,
            )
        }
    }
}
