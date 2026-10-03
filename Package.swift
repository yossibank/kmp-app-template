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
        url: "https://api.github.com/repos/yossibank/kmp-app-template/releases/assets/607384270.zip",
        checksum: "2dbd107049536ee0dde96936bc580af0fd9abe87f9cf7932191aec410339b248"
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
