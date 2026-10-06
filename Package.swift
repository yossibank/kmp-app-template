// swift-tools-version: 6.0
import PackageDescription

let shared: Target = if Context.environment["SHARED_DIR"] != nil {
    .binaryTarget(
        name: "Shared",
        path: "shared/build/XCFrameworks/debug/Shared.xcframework"
    )
} else {
    .binaryTarget(
        name: "Shared",
        url: "https://api.github.com/repos/yossibank/kmp-app-template/releases/assets/615680199.zip",
        checksum: "9b96f7a6aec13a58d5c26dabe55533a0ed873784790e557e3b57aa0b4edc76e7"
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
