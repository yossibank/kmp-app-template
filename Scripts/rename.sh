#!/bin/sh
set -eu

OLD_OWNER="yossibank"
OLD_PREFIX="com.yossibank"

if [ $# -ne 2 ]; then
    echo "Usage: Scripts/rename.sh <GitHub のオーナー> <パッケージの接頭辞>    例: Scripts/rename.sh acme com.acme" >&2
    exit 1
fi

OWNER="$1"
PREFIX="$2"

if ! printf '%s' "$OWNER" | grep -Eq '^[A-Za-z0-9][A-Za-z0-9-]*$'; then
    echo "GitHub のオーナーは英数字とハイフンで指定してください: $OWNER" >&2
    exit 1
fi

if ! printf '%s' "$PREFIX" | grep -Eq '^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$'; then
    echo "パッケージの接頭辞は小文字のドット区切りで指定してください（例: com.acme）: $PREFIX" >&2
    exit 1
fi

if [ -n "$(git status --porcelain)" ]; then
    echo "コミットされていない変更があります。先に整理してください。" >&2
    exit 1
fi

OLD_PATH="$(printf '%s' "$OLD_PREFIX" | tr . /)"
NEW_PATH="$(printf '%s' "$PREFIX" | tr . /)"

git grep -lIz -e "$OLD_OWNER" | xargs -0 perl -pi -e \
    "s/\\Q${OLD_PREFIX}\\E(?![A-Za-z0-9_])/${PREFIX}/g; s/(?<![A-Za-z0-9_.-])${OLD_OWNER}(?![A-Za-z0-9_-])/${OWNER}/g"

git ls-files | grep "/${OLD_PATH}/" | sed "s#/${OLD_PATH}/.*#/${OLD_PATH}#" | sort -u | while read -r dir; do
    parent="${dir%/"$OLD_PATH"}"
    mkdir -p "$(dirname "$parent/$NEW_PATH")"
    git mv "$dir" "$parent/$NEW_PATH"
    find "$parent" -type d -empty -delete
done

echo "${OLD_PREFIX} を ${PREFIX} に、${OLD_OWNER} を ${OWNER} に置き換えました。差分を確かめてコミットしてください。"
echo "共通コアは ${OWNER}/kmp-app-template から取得します。kmp-app-template を先にリリースするか、SHARED_DIR で手元のものを参照してください。"
echo "LICENSE の著作権者は書き換えていません。"
