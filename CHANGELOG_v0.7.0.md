# v0.7.0

- Removed DroidUP dependency.
- Removed host-created Android VirtualDisplay engine.
- Removed AOSP/native freeform dependency.
- Added official scrcpy-server v4.1 rootless engine through Shizuku shell UID.
- Added Binder FD handoff for scrcpy abstract Unix sockets.
- Added on-device H.264 MediaCodec decoder to TextureView.
- Added scrcpy control protocol for app launch, touch, keys and flex-display resize.
- Resets old OpenDex global freeform flags before sessions start.
- One scrcpy server/display per desktop app window.

- Protocol audit: implement scrcpy 4.1 codec-id + 12-byte session-packet framing (including flex-display session changes); media flags use config bit 62/keyframe bit 61.
- Wait for scrcpy display id and request fullscreen inner display before sending `START_APP`, reducing Android 16/OEM nested desktop decorations.
