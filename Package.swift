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
        url: "https://api.github.com/repos/yossibank/kmp-app-template/releases/assets/608804309.zip",
        checksum: "20b2b6a8f25d3b0288dc8f9f3b09048fbe4c851e203abfbc38e7b8daf03c766c"
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
