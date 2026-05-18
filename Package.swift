// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "BiangBiangUI",
    platforms: [.iOS(.v17)],
    products: [
        .library(name: "BiangBiangUI", targets: ["BiangBiangUI"])
    ],
    targets: [
        .target(
            name: "BiangBiangUI",
            swiftSettings: [.swiftLanguageMode(.v6)]
        ),
        // Test-support target: a sample app config that proves the config seam.
        // It lives OUTSIDE the library and depends on it like a real consumer.
        // Compiled in CI via the test target's dependency.
        .target(
            name: "ChineseExample",
            dependencies: ["BiangBiangUI"],
            path: "Examples/ChineseExample",
            resources: [.process("Resources/cantonese.json")],
            swiftSettings: [.swiftLanguageMode(.v6)]
        ),
        .target(
            name: "ArabicExample",
            dependencies: ["BiangBiangUI"],
            path: "Examples/ArabicExample",
            resources: [
                .process("Resources/quran.json"),
                .process("Resources/surah-names.json"),
                .process("Resources/vocab.plist"),
            ],
            swiftSettings: [.swiftLanguageMode(.v6)]
        ),
        .testTarget(
            name: "BiangBiangUITests",
            dependencies: ["BiangBiangUI", "ChineseExample", "ArabicExample"],
            swiftSettings: [.swiftLanguageMode(.v6)]
        )
    ]
)
