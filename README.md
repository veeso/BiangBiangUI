# BiangBiangUI

Shared dual-platform (iOS + Android) UI library for the script-to-Latin OCR
app family: BiangBiang Hanzi (Chinese → Pinyin/Jyutping), Harakat Lens
(Arabic → Latin + Quran), and future Japanese/Korean apps. Each app reduces to
one `BiangBiangConfig` plus a small `Transliterator`. The library renders every
screen and owns History, the rate-app prompt, TTS, and the OCR/Vision pipeline.

## Consuming the library

### iOS (Swift Package Manager)

```swift
.package(url: "https://github.com/veeso/BiangBiangUI", from: "0.1.2")
```

```swift
@main struct MyApp: App {
    var body: some Scene { WindowGroup { BiangBiangRootView(config: .myAppConfig) } }
}
```

### Android (JitPack)

```kotlin
// settings.gradle.kts -> dependencyResolutionManagement.repositories
maven(url = "https://jitpack.io")
```

```kotlin
implementation("com.github.veeso.BiangBiangUI:biangbiang-ui:0.1.2")
```

```kotlin
setContent { BiangBiangRoot(myAppConfig) }
```

## Building

### iOS

```bash
swift build
swift test
swiftformat ./Sources ./Tests ./Examples   # run whenever iOS code changes
```

### Android

```bash
cd android && ./gradlew :biangbiang-ui:assembleRelease
cd android && ./gradlew test lintDebug
```

## Examples

`Examples/` (iOS) and `android/examples/` contain sample configs — a Chinese
profile (Pinyin + Jyutping) and an Arabic profile with a Quran feature plugin —
that prove the config seam. They build in CI and are not shipping apps.

## License

Licensed under the Elastic License 2.0. See [LICENSE](./LICENSE).
