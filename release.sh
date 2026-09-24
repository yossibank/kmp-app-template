#!/bin/bash
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

if git rev-parse "$TAG" >/dev/null 2>&1 || gh release view "$TAG" >/dev/null 2>&1; then
    echo "${TAG} は既に存在します。バージョンを上げてください。" >&2
    exit 1
fi

if [ -n "$(git status --porcelain)" ]; then
    echo "コミットされていない変更があります。先に整理してください。" >&2
    exit 1
fi

BRANCH="$(git rev-parse --abbrev-ref HEAD)"
if [ "$BRANCH" != "main" ]; then
    echo "main 以外からはリリースしません: ${BRANCH}" >&2
    exit 1
fi

make verify

export GITHUB_ACTOR="${GITHUB_ACTOR:-$(gh api user --jq .login)}"
export GITHUB_TOKEN="${GITHUB_TOKEN:-$(gh auth token)}"

BASE_COMMIT="$(git rev-parse HEAD)"
STAGE="edited"

undo_edit() { git checkout -- "$BUILD_FILE" Package.swift >/dev/null 2>&1 || true; }
undo_draft() { gh release delete "$TAG" --yes >/dev/null 2>&1 || true; }
undo_commit() {
    git tag -d "$TAG" >/dev/null 2>&1 || true
    git reset -q --hard "$BASE_COMMIT"
}

on_error() {
    case "$STAGE" in
        edited)
            undo_edit
            ;;
        draft)
            undo_draft
            undo_edit
            ;;
        committed)
            undo_draft
            undo_commit
            ;;
        pushed)
            echo >&2
            echo "${TAG} は push 済みですが publish が終わっていません。" >&2
            echo "バージョンはまだ使えます。同じ番号のまま次で再開してください:" >&2
            echo "  ./gradlew :${MODULE}:publishAllPublicationsToGitHubPackagesRepository" >&2
            echo "  gh release edit ${TAG} --draft=false" >&2
            ;;
    esac
}
trap on_error ERR

echo "▶ ${TAG} のリリースを開始します"

sed -i '' "s/^version = \".*\"$/version = \"${VERSION}\"/" "$BUILD_FILE"
echo "  version = ${VERSION} を ${BUILD_FILE} に書き込みました"

./gradlew ":${MODULE}:packageXCFramework"
[ -f "$ZIP" ] || { echo "zip が生成されていません: $ZIP" >&2; exit 1; }
CHECKSUM="$(cat "$CHECKSUM_FILE")"

STAGE="draft"
gh release create "$TAG" --draft --title "$TAG" --generate-notes >/dev/null
gh release upload "$TAG" "$ZIP" >/dev/null

ASSET_URL=""
for _ in $(seq 1 10); do
    ASSET_URL="$(gh release view "$TAG" --json assets --jq '.assets[0].apiUrl // empty')"
    [ -n "$ASSET_URL" ] && break
    sleep 1
done
[ -n "$ASSET_URL" ] || { echo "アセットの API URL を取得できませんでした" >&2; exit 1; }

ASSET_URL="${ASSET_URL}.zip"

sed -i '' -E \
    -e "s|^( *url: )\".*\",$|\\1\"${ASSET_URL}\",|" \
    -e "s|^( *checksum: )\".*\"$|\\1\"${CHECKSUM}\"|" \
    Package.swift
grep -qF "url: \"${ASSET_URL}\"," Package.swift && grep -qF "checksum: \"${CHECKSUM}\"" Package.swift ||
    { echo "Package.swift の url / checksum を書き換えられませんでした" >&2; false; }

STAGE="committed"
git add Package.swift "$BUILD_FILE"
git commit -q -m "Release ${TAG}"
git tag -a "$TAG" -m "${FRAMEWORK} ${VERSION}"

STAGE="pushed"
git push -q origin HEAD
git push -q origin "$TAG"

./gradlew ":${MODULE}:publishAllPublicationsToGitHubPackagesRepository"
gh release edit "$TAG" --tag "$TAG" --draft=false >/dev/null

trap - ERR

echo
echo "✅ ${TAG} をリリースしました"
echo "   asset    : ${ASSET_URL}"
echo "   checksum : ${CHECKSUM}"
echo
echo "次にやること:"
echo "  ios-app-template     : SPM の参照を ${TAG} に更新"
echo "  android-app-template : libs.versions.toml の shared を ${VERSION} に更新"
