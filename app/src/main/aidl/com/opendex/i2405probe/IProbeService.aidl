package com.opendex.i2405probe;

interface IProbeService {
    String startProbe();
    String getLog();
    void stopProbe();
    void destroy();
}
