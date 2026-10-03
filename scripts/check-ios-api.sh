#!/bin/sh
set -eu

dump="shared/api/shared.klib.api"

violations=$(grep -nE '^ *(open|abstract) (class|interface|fun interface) |kotlinx\.coroutines\.flow' "$dump" || true)

if [ -n "$violations" ]; then
    echo "iOS に出せない宣言が $dump にあります（sealed でない interface、open / abstract class、Flow）。" >&2
    echo "$violations" >&2
    exit 1
fi

echo "iOS に出す宣言に、sealed でない interface・open / abstract class・Flow はありません"
