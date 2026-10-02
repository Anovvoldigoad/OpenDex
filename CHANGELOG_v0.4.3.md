# v0.4.3

- Keeps the original DroidUP launcher APK byte-identical.
- Removes the lint-invalid `ComponentActivity.dispatchKeyEvent()` override/call.
- Hardware-key forwarding now lives on the focused `SurfaceView` via `OnKeyListener`.
- Back remains handled by AndroidX `OnBackPressedDispatcher` for legacy and predictive back.
- Pins GitHub runner to `ubuntu-24.04`.
- Upgrades `actions/checkout` and `actions/upload-artifact` to Node-24 releases.
- Repairs preflight so all guards run before the final PASS/FAIL.
