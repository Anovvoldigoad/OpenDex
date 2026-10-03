# Build status — v0.7.2

Static checks performed in the generation environment:

- Android XML parse: PASS
- Java structural/source checks: PASS
- GitHub workflow YAML parse: PASS
- OpenDex preflight source guards: PASS once the CI-downloaded official scrcpy-server v4.1 asset is present
- Root-flat archive layout: PASS

Runtime changes requiring phone validation:

1. Open Chrome window.
2. Open a second app while Chrome remains open.
3. Confirm both sessions stream simultaneously.
4. Confirm the second window no longer reports `scrcpy-server missing`.
5. Confirm a target app is started fresh on its own virtual display rather than reusing an existing phone-display task.
