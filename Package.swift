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
        url: "https://api.github.com/repos/yossibank/kmp-app-template/releases/assets/609116514.zip",
        checksum: "cd8c1768f0df3c7f80cc0176ba5f3ed35a8e543aaa273dc32d887beb5a3f064e"
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
