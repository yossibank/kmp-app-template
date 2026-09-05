import os
import pathlib
import re
import sys

consumer = pathlib.Path(sys.argv[1]).resolve()
core = pathlib.Path.cwd().resolve()

# .package(path:) の識別子はディレクトリ名から決まる。ios 側は
# .product(package: "kmp-app-template") と書いているため、名前が一致しないと
# 解決に失敗する。CI の作業ディレクトリ名がリポジトリ名になることに依存している。
assert core.name == "kmp-app-template", f"共通コアのディレクトリ名が想定と違う: {core.name}"

xcframework = core / "shared/build/XCFrameworks/release/Shared.xcframework"
assert xcframework.is_dir(), f"XCFramework が無い: {xcframework}"

manifest = core / "Package.swift"
s = manifest.read_text()
s, n = re.subn(
    r'url:\s*"[^"]*",\s*\n\s*checksum:\s*"[^"]*"',
    'path: "shared/build/XCFrameworks/release/Shared.xcframework"',
    s,
)
assert n == 1, f"binaryTarget を path 指定に書き換えられなかった (n={n})"
manifest.write_text(s)

ios_manifest = consumer / "Package/Package.swift"
s = ios_manifest.read_text()
s, n = re.subn(
    r'\.package\(\s*url:\s*"[^"]*kmp-app-template[^"]*",\s*exact:\s*"[^"]*"\s*,?\s*\)',
    f'.package(path: "{core}")',
    s,
)
assert n == 1, f"ios の依存を path 指定に書き換えられなかった (n={n})"
ios_manifest.write_text(s)

resolved = consumer / "AppTemplate.xcworkspace/xcshareddata/swiftpm/Package.resolved"
if resolved.exists():
    resolved.unlink()

print(f"iOS アプリを {xcframework} に向けた")
