# Build status — v0.6.1

Target architecture: custom desktop windows backed by one private trusted virtual display per app.

Static checks performed in this workspace:
- source preflight: expected PASS
- Android XML parse: checked
- per-app PRIVATE display guard: present
- per-display FULLSCREEN guard: present
- global `force_resizable_activities`: absent
- DroidUP dependency: absent
- AOSP task resize freeform: absent

A full Android SDK build is performed by the included GitHub Actions workflow.
