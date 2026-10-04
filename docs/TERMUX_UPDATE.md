# Phone-only repo replacement (preserve `.git`)

Example for this release:

```sh
cd /storage/emulated/0/OpenDex || exit 1
find . -mindepth 1 -maxdepth 1 ! -name '.git' -exec rm -rf -- {} +
unzip -o /storage/emulated/0/Download/OpenDex-ScrcpyEngine-v0.7.2-MULTISESSION-FIX-SOURCE.zip -d /storage/emulated/0/OpenDex/
git add . && git commit -m "v0.7.2 multisession fix" && git push
```

The distribution ZIP is intentionally root-flat, so extracting it directly into the repository places `app/`, `.github/`, `scripts/`, and the Gradle files at repository root.
