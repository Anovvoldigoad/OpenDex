package com.opendex.desktop;

import android.view.Surface;

interface IWindowShellService {
    int remoteUid() = 1;
    String exec(String command) = 2;
    String createWindowDisplay(in Surface surface, int width, int height, int dpi, String name, int sdkInt) = 3;
    String resizeWindowDisplay(int displayId, int width, int height, int dpi) = 4;
    String launchComponent(int displayId, String packageName, String componentName) = 5;
    void releaseWindowDisplay(int displayId) = 6;
    boolean injectPointer(int displayId, int action, float x, float y, long downTime, long eventTime) = 7;
    boolean injectKey(int displayId, int keyCode) = 8;
    void destroy() = 16777114;
}
