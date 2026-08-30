#!/bin/bash
#
# 共通コアをリリースする。
#
#   ./release.sh 0.3.0
#
# 実行すると次が起きる:
#   1. shared/build.gradle.kts の version を書き換える
#   2. XCFramework を zip 化し checksum を算出する
#   3. Android 向けに GitHub Packages へ publish する
#   4. XCFramework を GitHub Release のアセットとして上げる
#   5. Package.swift をそのアセットの URL と checksum で生成する
#   6. コミットしてタグを打ち、リリースを公開する
#
set -euo pipefail

MODULE="shared"
FRAMEWORK="Shared"

if [ $# -ne 1 ]; then
    echo "Usage: ./release.sh <version>    例: ./release.sh 0.3.0" >&2
    exit 1
fi

VERSION="$1"
TAG="v${VERSION}"
BUILD_FILE="${MODULE}/build.gradle.kts"
ZIP="${MODULE}/build/spm/${FRAMEWORK}.xcframework.zip"
CHECKSUM_FILE="${MODULE}/build/spm/checksum.txt"

if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "version は semver で指定してください（例: 0.3.0）: $VERSION" >&2
    exit 1
fi

# リリース済みバージョンは上書きしない。
# GitHub Packages は同一バージョンの再 publish を 409 で拒否するため、
# タグだけ差し替えると Maven 側と整合しなくなる。
if git rev-parse "$TAG" >/dev/null 2>&1 || gh release view "$TAG" >/dev/null 2>&1; then
    echo "${TAG} は既に存在します。バージョンを上げてください。" >&2
    exit 1
fi

if [ -n "$(git status --porcelain)" ]; then
    echo "コミットされていない変更があります。先に整理してください。" >&2
    exit 1
fi

# Gradle の GitHub Packages 認証。
# このスクリプトは既に gh に依存しているので、認証情報もそこから導出する。
# GitHub Actions 上では両方あらかじめ設定されているため、この行は素通りする。
export GITHUB_ACTOR="${GITHUB_ACTOR:-$(gh api user --jq .login)}"
export GITHUB_TOKEN="${GITHUB_TOKEN:-$(gh auth token)}"

echo "▶ ${TAG} のリリースを開始します"

# 1. version を先に確定させる。
#    このあとの publish がこの値を読むので、ビルドより前でなければならない。
sed -i '' "s/^version = \".*\"$/version = \"${VERSION}\"/" "$BUILD_FILE"
echo "  version = ${VERSION} を ${BUILD_FILE} に書き込みました"

# 2. XCFramework の zip と checksum
./gradlew ":${MODULE}:packageXCFramework"
[ -f "$ZIP" ] || { echo "zip が生成されていません: $ZIP" >&2; exit 1; }
CHECKSUM="$(cat "$CHECKSUM_FILE")"

# 3. Android 向けの Maven publish
./gradlew ":${MODULE}:publishAllPublicationsToGitHubPackagesRepository"

# 4. ドラフトリリースを作り、アセットを上げて API URL を得る。
#    ドラフトならタグがまだ無くても作れるので、
#    「アセットの URL が確定しないと Package.swift を書けない」順序の循環を避けられる。
cleanup_draft() { gh release delete "$TAG" --yes >/dev/null 2>&1 || true; }
trap cleanup_draft ERR

gh release create "$TAG" --draft --title "$TAG" --generate-notes >/dev/null
gh release upload "$TAG" "$ZIP" >/dev/null

ASSET_URL=""
for _ in $(seq 1 10); do
    ASSET_URL="$(gh release view "$TAG" --json assets --jq '.assets[0].apiUrl // empty')"
    [ -n "$ASSET_URL" ] && break
    sleep 1
done
[ -n "$ASSET_URL" ] || { echo "アセットの API URL を取得できませんでした" >&2; exit 1; }

# SPM は URL の拡張子で妥当性を判定するため .zip を付ける。
# GitHub 側は末尾の .zip を無視して同じバイナリを返す。
ASSET_URL="${ASSET_URL}.zip"

# 5. Package.swift を生成
cat > Package.swift <<EOF
// swift-tools-version: 6.0
// このファイルは release.sh が生成する。手で編集しないこと。
import PackageDescription

let package = Package(
    name: "${FRAMEWORK}",
    products: [
        .library(name: "${FRAMEWORK}", targets: ["${FRAMEWORK}"])
    ],
    targets: [
        .binaryTarget(
            name: "${FRAMEWORK}",
            url: "${ASSET_URL}",
            checksum: "${CHECKSUM}"
        )
    ]
)
EOF

# 6. コミットしてタグを打つ
git add Package.swift "$BUILD_FILE"
git commit -q -m "Release ${TAG}"
git tag -a "$TAG" -m "${FRAMEWORK} ${VERSION}"
git push -q origin HEAD
git push -q origin "$TAG"

trap - ERR
gh release edit "$TAG" --tag "$TAG" --draft=false >/dev/null

echo
echo "✅ ${TAG} をリリースしました"
echo "   asset    : ${ASSET_URL}"
echo "   checksum : ${CHECKSUM}"
echo
echo "次にやること:"
echo "  ios-app-template     : SPM の参照を ${TAG} に更新"
echo "  android-app-template : libs.versions.toml の shared を ${VERSION} に更新"
