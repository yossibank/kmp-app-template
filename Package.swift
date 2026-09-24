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
            url: "https://api.github.com/repos/yossibank/kmp-app-template/releases/assets/585992271.zip",
            checksum: "2ca2a29e1b757df4452af25b188ee967e511f312c3eb9163807c5e84d7775ac1"
        )
    ]
)
