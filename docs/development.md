# 開発の手順

> [!NOTE]
> 変更したときに通すコマンドを書く。
> 公開 API のダンプと、手元からのリリースも書く。
> 実行環境の版は [README](../README.md#-実行環境) にある。

## 🚀 使い方

| コマンド | いつ使うか |
| --- | --- |
| `make verify` | 変更したら通す |
| `make api` | 公開 API を変えたら、`shared/api/` のダンプを更新する |

> [!IMPORTANT]
> 公開 API のダンプと差分があると `make verify` が落ちる。

既存のアプリに組み込む手順は [integration](integration.md) にある。

## 📦 リリース

GitHub Actions の Release ワークフローにバージョン（semver）を渡して実行する。流れは [README](../README.md#-リリース) の図にある。

手元から実行するときは、AWS にログインしてから次を実行する。

```sh
./release.sh <version>
```
