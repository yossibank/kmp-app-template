#!/bin/sh
set -eu

if [ $# -ne 1 ]; then
    echo "Usage: scripts/check-app-versions.sh <Android アプリのディレクトリ>    例: scripts/check-app-versions.sh ../android" >&2
    exit 1
fi

own="gradle/libs.versions.toml"
app="$1/gradle/libs.versions.toml"

version() { sed -n "s/^$1 = \"\(.*\)\"$/\1/p" "$2"; }

failed=0

for key in kotlin kotlinx-coroutines kotlinx-serialization ktor; do
    ours="$(version "$key" "$own")"
    theirs="$(version "$key" "$app")"

    [ -n "$theirs" ] || continue

    newest="$(printf '%s\n%s\n' "$ours" "$theirs" | sort -V | tail -n 1)"

    if [ "$ours" != "$theirs" ] && [ "$newest" = "$ours" ]; then
        echo "${key}: 共通コアは ${ours}、アプリは ${theirs} です。アプリより新しくしないでください。" >&2
        failed=1
    fi
done

[ "$failed" -eq 0 ] || exit 1

echo "Kotlin・kotlinx・Ktor は、アプリのバージョンを超えていません"
