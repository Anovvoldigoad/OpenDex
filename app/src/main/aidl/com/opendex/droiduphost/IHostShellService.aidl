package com.opendex.droiduphost;

import android.view.Surface;

interface IHostShellService {
    String exec(String command) = 1;
    int remoteUid() = 2;
    boolean beginPayload() = 3;
    boolean appendPayload(in byte[] data) = 4;
    String installPayload() = 5;
    String createDesktopDisplay(in Surface surface, int width, int height, int dpi, int sdkInt) = 6;
    void releaseDesktopDisplay() = 7;
    boolean injectPointer(int displayId, int action, float x, float y, long downTime, long eventTime) = 8;
    boolean injectKey(int displayId, int keyCode) = 9;
    String forcePackageTaskWindowingUndefined(String packageName, int displayId) = 10;
    void destroy() = 16777114;
}
