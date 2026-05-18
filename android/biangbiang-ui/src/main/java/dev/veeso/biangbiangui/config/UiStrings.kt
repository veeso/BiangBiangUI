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
            val o = overrides ?: return UiStrings()
            return UiStrings(
                inputTitle = o["inputTitle"] ?: "Text",
                outputTitle = o["outputTitle"] ?: "Transliteration",
                translationTitle = o["translationTitle"] ?: "Translation",
                paste = o["paste"] ?: "Paste",
                copy = o["copy"] ?: "Copy",
                listen = o["listen"] ?: "Listen",
                stop = o["stop"] ?: "Stop",
                save = o["save"] ?: "Save",
                savedToHistory = o["savedToHistory"] ?: "Saved to History",
                textCopied = o["textCopied"] ?: "Text copied",
                clearAll = o["clearAll"] ?: "Clear All",
                clearAllConfirm = o["clearAllConfirm"] ?: "Delete all history?",
                original = o["original"] ?: "Original",
                transliterated = o["transliterated"] ?: "Transliterated",
                historyEmpty = o["historyEmpty"] ?: "No history yet",
                cameraDisabledTitle = o["cameraDisabledTitle"] ?: "Camera access disabled",
                cameraDisabledMessage = o["cameraDisabledMessage"]
                    ?: "Enable camera access in Settings to scan text.",
                openSettings = o["openSettings"] ?: "Open Settings",
                tapToCopyLongPressToSave = o["tapToCopyLongPressToSave"]
                    ?: "Tap to copy · long-press to save",
                translate = o["translate"] ?: "Translate",
                reportBug = o["reportBug"] ?: "Report a bug",
                openGithubIssues = o["openGithubIssues"] ?: "Open GitHub Issues",
                sendEmail = o["sendEmail"] ?: "Send Email",
                rateTitle = o["rateTitle"] ?: "Enjoying the app?",
                rateMessage = o["rateMessage"] ?: "A quick rating really helps.",
                rateNow = o["rateNow"] ?: "Rate now",
                notNow = o["notNow"] ?: "Not now",
                dontAskAgain = o["dontAskAgain"] ?: "Don't ask again",
                translationLanguage = o["translationLanguage"] ?: "Translation language",
                tabText = o["tabText"] ?: "Text",
                tabCamera = o["tabCamera"] ?: "Camera",
                tabHistory = o["tabHistory"] ?: "History",
                tabSettings = o["tabSettings"] ?: "Settings",
            )
        }
    }
}
