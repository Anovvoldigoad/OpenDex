# Build Status — v0.4.2

Static patch status:
- Trusted-display architecture retained from v0.4.1.
- Deprecated `onBackPressed()` override removed.
- AndroidX `OnBackPressedDispatcher` added for legacy + predictive back.
- CI artifact renamed to v0.4.2 so old-source builds are obvious.
- Lint is configured as a required CI step.

A successful GitHub run should show both `:app:assembleDebug` and `:app:lintDebug` green and produce `DroidUP-AndroidHost-v0.4.2-trusted-debug`.
