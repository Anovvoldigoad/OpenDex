# v0.5.0 status

- Original DroidUP launcher payload: unchanged.
- Trusted shell-owned VirtualDisplay: retained.
- Direct InputManager injection: retained.
- **New:** TaskDisplayArea is explicitly switched to `WINDOWING_MODE_FREEFORM` (5).
- **New:** virtual desktop is locked landscape and app orientation requests are ignored.
- **New:** session aborts with diagnostics when per-display freeform cannot be enabled.

Hardware verification is still required because OEM Android 16 builds may differ in WindowManager shell support.
