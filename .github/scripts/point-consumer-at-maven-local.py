import os, pathlib, re, sys

CHECK_VERSION = "0.0.0-consumer-check"
consumer = pathlib.Path(sys.argv[1])

# 共通コアを、リモートに存在しないバージョンで publish させる。
# これにより GitHub Packages から解決される可能性が無くなり、
# 今ビルドした成果物を検証したことが保証される。
build = pathlib.Path("shared/build.gradle.kts")
s = build.read_text()
s, n = re.subn(r'^version = ".*"$', f'version = "{CHECK_VERSION}"', s, flags=re.M)
assert n == 1, f"shared/build.gradle.kts の version を書き換えられなかった (n={n})"
build.write_text(s)

toml = consumer / "gradle/libs.versions.toml"
s = toml.read_text()
s, n = re.subn(r'^shared = ".*"$', f'shared = "{CHECK_VERSION}"', s, flags=re.M)
assert n == 1, f"libs.versions.toml の shared を書き換えられなかった (n={n})"
toml.write_text(s)

settings = consumer / "settings.gradle.kts"
s = settings.read_text()
anchor = "    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)\n    repositories {\n"
assert s.count(anchor) == 1, "settings.gradle.kts の repositories ブロックを特定できなかった"
settings.write_text(s.replace(anchor, anchor + "        mavenLocal()\n"))

print(f"共通コアを {CHECK_VERSION} として mavenLocal 経由で解決させる")
