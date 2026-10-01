package com.opendex.launcher;

interface IUserShellService {
    String exec(String command) = 1;
    int remoteUid() = 2;
    void destroy() = 16777114;
}
