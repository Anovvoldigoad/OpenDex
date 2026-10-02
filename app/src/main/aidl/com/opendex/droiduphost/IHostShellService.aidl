package com.opendex.droiduphost;

interface IHostShellService {
    String exec(String command) = 1;
    int remoteUid() = 2;
    boolean beginPayload() = 3;
    boolean appendPayload(in byte[] data) = 4;
    String installPayload() = 5;
    void destroy() = 16777114;
}
