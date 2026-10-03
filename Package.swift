// swift-tools-version: 6.0
import PackageDescription

let shared: Target = if Context.environment["SHARED_DIR"] != nil {
    .binaryTarget(
        name: "Shared",
        path: "shared/build/XCFrameworks/release/Shared.xcframework"
    )
} else {
    .binaryTarget(
        name: "Shared",
        url: "https://api.github.com/repos/yossibank/kmp-app-template/releases/assets/607162454.zip",
        checksum: "0d7e6a01e58c1f309f8599f66c36ef2c2ebe4919b4529b817f065c36520edde9"
    )
}

let package = Package(
    name: "Shared",
    products: [
        .library(name: "Shared", targets: ["Shared"])
    ],
    targets: [
        shared
    ]
)
