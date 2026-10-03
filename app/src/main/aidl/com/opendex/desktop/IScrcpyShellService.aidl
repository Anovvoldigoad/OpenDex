package com.opendex.desktop;

import android.os.ParcelFileDescriptor;

interface IScrcpyShellService {
    int remoteUid() = 1;
    boolean installScrcpyServer(in byte[] data, String expectedSha256) = 2;
    String prepareEnvironment() = 3;
    String startScrcpyServer(int scid, int width, int height, int dpi, int bitRate, int maxFps) = 4;
    ParcelFileDescriptor connectScrcpy(int scid) = 5;
    String sessionInfo(int scid) = 6;
    void stopScrcpyServer(int scid) = 7;
    String exec(String command) = 8;
    String prepareSessionDisplay(int scid) = 9;
    void destroy() = 16777114;
}
