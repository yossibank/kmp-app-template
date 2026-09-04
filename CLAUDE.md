# kmp-app-template

iOS / Android で共有するロジックの Kotlin Multiplatform ライブラリ。アプリ本体は含まない。

## 検証

変更したら必ず通す。通らないものは完了ではない。

```sh
make verify   # XCFramework のビルド + 全ターゲットのテスト
```

iOS ターゲットのビルドには Xcode が必要。`xcode-select -p` が CommandLineTools を
指している場合は `DEVELOPER_DIR` を設定する。

## 規約

- ビジネスロジックのテストは `commonTest` に置く。1 度書けば両OSで実行される。
- `expect`/`actual` は OS の薄いシムのみ（時刻・UUID・パス・鍵保管・ログ出力）。
  ビジネスロジックの分岐には使わず、interface + DI で注入する。
- SKIE を外さない。suspend / Flow / sealed interface が ObjC の completionHandler や
  SharedKotlinx_coroutines_coreFlow として iOS 側に露出するようになる。
- iOS へは shared モジュールが XCFramework 1 枚として公開される（umbrella）。
- iOS に公開する型は data class / sealed interface / enum のみ。
- 例外を投げない。結果は sealed な型で返す。
- `internal` を既定にする。公開するのは必要なものだけ。
- バージョンは `gradle/libs.versions.toml` にのみ書く。build.gradle.kts に直書きしない。

## OpenAPI

`shared/openapi/pokeapi.yml` から**モデルだけ**を生成する。HTTP クライアントは
手書き（`PokemonApi.kt`）。openapi-generator の multiplatform テンプレートは
Ktor 1.6.7 / kotlinx-serialization 1.2.1 前提で、このプロジェクトの Ktor 3.3.3 とは
2 メジャー分ずれているため使わない。

- 生成先のパッケージは `com.yossibank.shared.generated`。ktlint はこの名前で除外している
- `dateLibrary` の指定は必須（multiplatform は `string` か `kotlinx-datetime` のみ）
- 生成対象は `globalProperties` の `models` で絞る。`PokemonDetail` は 20 以上の入れ子を引き連れてくる

## SKIE の変換で確認したこと

実際に書いて生成物を確認した結果。

| Kotlin | Swift | 結果 |
| --- | --- | --- |
| `suspend fun` | `func ... async throws` | 変換される |
| `sealed interface` | `enum __Sealed` + `onEnum(of:)` | 変換される |
| `Flow<T>` | `SkieSwiftFlow<T>` | **変換されない** |

**Kotlin の引数既定値は Swift に渡らない。** iOS からは全引数を要求する
イニシャライザしか見えないため、既定の依存だけで作れる経路を別に用意すること。
`PokemonApi` は `internal constructor` と引数なしの `constructor()` に分けている。

**Flow を公開 API に置かないこと。** SKIE 0.10.14 では関数・プロパティどちらでも
変換されず、Swift 側からは生の `id<SharedFlow>` に見える。`SkieSwiftFlow` の定義自体は
生成されるが署名の置き換えが起きない。coroutines を `api` にしても `export` しても変わらず、
`FlowInterop` の設定項目もこのバージョンには存在しない。原因未特定。

## CI の消費側検証

共通コアの変更が両アプリを壊さないことを CI で確認している。Android は
mavenLocal 経由でビルドとユニットテスト、iOS はビルドのみ。どちらも実行時までは見ていない。

**公開 API は `suspend` と `sealed interface` になり、SKIE の変換を通るようになった。**
両 OS で実通信して一覧が出ることは手で確認済みだが、CI では確認していない。
実行時テストを足す場合のコストは約 120 秒（シミュレータを事前起動しない場合は 337 秒）。
足すときは `xcrun simctl boot` を先のステップに置いて起動待ちを重ねること。

## 破壊的変更の出し方

公開 API を消す・シグネチャを変えるときは 1 リリースで壊さない。非推奨にした
リリースを 1 つ挟み、消費側が乗り換えてから次のリリースで消す。

1. 消す API を `@Deprecated` で残したままリリースする
2. 消費側 2 リポジトリを新しい API に乗り換えて main へ push する
3. 次のリリースで API を消す

**1 リリースで消すと必ずどこかの main が赤くなる。** CI の消費側検証は消費側の
main を clone してビルドするため、消費側が古い API を参照したまま共通コアだけ
先に変わると `Unresolved reference` で落ちる。逆に消費側を先に更新すると、参照する
新バージョンがまだ公開されていないため消費側の CI が落ちる。この循環は非推奨を
1 段挟む以外に解けない。

v0.7.0 で `Greeting` を予告なく消し、実際にこれで両方の CI を落としている。

## やってはいけない

- `org.jetbrains.kotlin.android` を適用しない（AGP 9 でエラーになる）
- `com.android.library` + `androidTarget()` を使わない。
  `com.android.kotlin.multiplatform.library` と `kotlin { android { } }` を使う
- トップレベルの `android { }` ブロックを書かない
- `.gitignore` に `*.jar` を追加しない（`gradle-wrapper.jar` が消える）
- `compileSdk` を 37 未満に下げない（AndroidX の最新版が要求する）
- publish 済みのバージョンを再 publish しない（GitHub Packages は 409 を返す。必ず version を上げる）
- detekt を安易に追加しない。1.23.x は JDK 25 上で動かず、2.x は alpha しかない（ktlint のみ採用）
