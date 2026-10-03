# Third-party notice

OpenDex Mi Engine v0.6.0 is a GPL-3.0 prototype inspired by the architecture of
Mi-Freeform 3 by sunshine0523 (https://github.com/sunshine0523/Mi-Freeform).

The key architectural idea reused is: render each Android app on its own virtual display
into a Surface/Texture owned by a custom window, and forward input to that display instead
of relying on the OEM/AOSP native freeform window frame.

This OpenDex implementation uses new project code and a Shizuku UserService rather than
Mi-Freeform's system_server/Magisk injection path. The project is distributed under GPL-3.0
so future direct reuse of compatible Mi-Freeform code can preserve license obligations.
