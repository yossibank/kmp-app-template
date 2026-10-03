#!/bin/sh
set -eu

if [ $# -ne 1 ]; then
    echo "Usage: scripts/open-app-prs.sh <version>    例: scripts/open-app-prs.sh 0.3.0" >&2
    exit 1
fi

VERSION="$1"
OWNER="${GITHUB_REPOSITORY_OWNER:-$(git config --get remote.origin.url | sed -E 's#.*github\.com[:/]([^/]+)/.*#\1#')}"
BRANCH="chore/shared-${VERSION}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

bump_android() {
    perl -pi -e "s/^shared = \".*\"\$/shared = \"${VERSION}\"/" gradle/libs.versions.toml
}

bump_ios() {
    perl -pi -e "s/(kmp-app-template\\.git\", exact: )\"[^\"]*\"/\${1}\"${VERSION}\"/" Package/Package.swift
    xcodebuild -resolvePackageDependencies -workspace AppTemplate.xcworkspace -scheme AppTemplate >/dev/null
}

open_pr() {
    repo="$1"
    bump="$2"
    dir="${WORK}/${repo}"

    git clone -q --depth 1 "https://github.com/${OWNER}/${repo}.git" "$dir"
    (
        cd "$dir"
        "$bump"

        if git diff --quiet; then
            echo "${repo}: すでに ${VERSION} です"
            exit 0
        fi

        git switch -q -c "$BRANCH"
        git commit -qam "Use shared core ${VERSION}"
        git push -q origin "$BRANCH"
        gh pr create -R "${OWNER}/${repo}" --base main --head "$BRANCH" \
            --title "Use shared core ${VERSION}" \
            --body "kmp-app-template ${VERSION} のリリースで自動で開いた PR です。"
    )
}

open_pr android-app-template bump_android
open_pr ios-app-template bump_ios
