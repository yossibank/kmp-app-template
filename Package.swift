// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "Shared",
    products: [
        .library(name: "Shared", targets: ["Shared"])
    ],
    targets: [
        .binaryTarget(
            name: "Shared",
            url: "https://api.github.com/repos/yossibank/kmp-app-template/releases/assets/578972991.zip",
            checksum: "a8209094d40f07669bede2b5cc320d069a5a71446d6693e5c77b657d1fb17362"
        )
    ]
)
