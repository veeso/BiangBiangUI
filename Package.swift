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
        .testTarget(
            name: "BiangBiangUITests",
            dependencies: ["BiangBiangUI"],
            swiftSettings: [.swiftLanguageMode(.v6)]
        )
    ]
)
