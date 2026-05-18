# Configuration

`BiangBiangConfig` is the single injection point for a consuming app. This
page documents the `strings` override map.

## String overrides

The library ships English defaults in `UIStrings` (iOS,
`Sources/BiangBiangUI/Config/UIStrings.swift`) and `UiStrings` (Android,
`config/UiStrings.kt`). A consuming app overrides any subset by passing a
`[String: String]` / `Map<String, String>` keyed by the property name; unset
keys fall back to the English default. The key set is identical on both
platforms.

```swift
BiangBiangConfig(
    // ...
    strings: [
        "inputTitle": "Hanzi",
        "outputTitle": "Pinyin",
        "appSubtitle": "Convert Hanzi to Pinyin",
    ]
)
```

### Keys

| Key                        | Default                                          | Used in                                  |
| -------------------------- | ------------------------------------------------ | ---------------------------------------- |
| `appSubtitle`              | `""` (empty — no subtitle rendered)              | Text screen tagline under the app name   |
| `inputTitle`               | `Text`                                           | Text screen input section title          |
| `outputTitle`              | `Transliteration`                                | Text screen output section title         |
| `translationTitle`         | `Translation`                                    | Text screen translation section title    |
| `paste`                    | `Paste`                                          | Text screen input paste action           |
| `copy`                     | `Copy`                                           | Output / translation copy action         |
| `listen`                   | `Listen`                                         | TTS play button                          |
| `stop`                     | `Stop`                                           | TTS stop button                          |
| `save`                     | `Save`                                           | Text screen save-to-history button       |
| `savedToHistory`           | `Saved to History`                               | Save confirmation toast                  |
| `textCopied`               | `Text copied`                                    | Copy confirmation toast                  |
| `clearAll`                 | `Clear All`                                      | History clear-all button                 |
| `clearAllConfirm`          | `Delete all history?`                            | History clear-all confirmation           |
| `original`                 | `Original`                                       | History row original label               |
| `transliterated`           | `Transliterated`                                 | History row transliteration label        |
| `historyEmpty`             | `No history yet`                                 | History empty state                      |
| `cameraDisabledTitle`      | `Camera access disabled`                         | Camera permission screen title           |
| `cameraDisabledMessage`    | `Enable camera access in Settings to scan text.` | Camera permission screen message         |
| `openSettings`             | `Open Settings`                                  | Camera permission screen settings button |
| `tapToCopyLongPressToSave` | `Tap to copy · long-press to save`               | Camera live overlay hint                 |
| `translate`                | `Translate`                                      | Translation section button               |
| `reportBug`                | `Report a bug`                                   | Settings screen support section          |
| `openGithubIssues`         | `Open GitHub Issues`                             | Settings screen support section          |
| `sendEmail`                | `Send Email`                                     | Settings screen support section          |
| `rateTitle`                | `Enjoying the app?`                              | Rate-app prompt title                    |
| `rateMessage`              | `A quick rating really helps.`                   | Rate-app prompt message                  |
| `rateNow`                  | `Rate now`                                       | Rate-app prompt confirm button           |
| `notNow`                   | `Not now`                                        | Rate-app prompt defer button             |
| `dontAskAgain`             | `Don't ask again`                                | Rate-app prompt dismiss button           |
| `translationLanguage`      | `Translation language`                           | Settings screen translation language     |
| `tabText`                  | `Text`                                           | Text tab label                           |
| `tabCamera`                | `Camera`                                         | Camera tab label                         |
| `tabHistory`               | `History`                                        | History tab label                        |
| `tabSettings`              | `Settings`                                       | Settings tab label                       |

> An unknown key in the override map is ignored. Keys are case-sensitive and
> must match the property name exactly.
