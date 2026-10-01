# Phone-only publish with Termux

This is an optional route if you do not connect GitHub directly in ChatGPT.

1. Extract `OpenDexLauncher_v0.3.3-GITHUB-ACTIONS.zip` on the phone.
2. In Termux:

```sh
pkg update
pkg install git gh
cd /path/to/OpenDexLauncher_v0.3.3-GITHUB
git init
git add .
git commit -m "Initial OpenDex v0.3.3"
git branch -M main
gh auth login
gh repo create OpenDexLauncher --public --source=. --remote=origin --push
```

3. Open the new repository in GitHub.
4. Open **Actions → Build OpenDex APK → Run workflow**.
5. After the job passes, open its **Artifacts** section and download `OpenDex-v0.3.3-debug`.
6. Extract the artifact ZIP and install `OpenDex-v0.3.3-debug.apk`.

`gh auth login` opens GitHub's normal authentication flow. Do not paste GitHub tokens into source files or commits.
