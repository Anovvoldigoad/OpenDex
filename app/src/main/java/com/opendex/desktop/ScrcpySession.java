package com.opendex.desktop;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Minimal on-device scrcpy 4.1 client.
 *
 * The official scrcpy-server creates/captures a new display as shell. OpenDex only decodes
 * its H.264 stream into our TextureView and forwards control messages. No AOSP freeform
 * window is used by the OpenDex host.
 */
public final class ScrcpySession {
    public interface Listener {
        void onState(String state);
        void onReady(int videoWidth, int videoHeight);
        void onVideoSizeChanged(int width, int height);
        void onFatal(String error);
    }

    private static final int CODEC_H264 = 0x68323634; // "h264"
    // scrcpy 4.1 media packet flags. Bit 63 is reserved for *session* packets.
    private static final long FLAG_CONFIG = 1L << 62;
    private static final long FLAG_KEY = 1L << 61;
    private static final long PTS_MASK = FLAG_KEY - 1;
    private static final int STREAM_HEADER_SIZE = 12;

    private static final int CTRL_INJECT_KEYCODE = 0;
    private static final int CTRL_INJECT_TOUCH = 2;
    private static final int CTRL_BACK_OR_SCREEN_ON = 4;
    private static final int CTRL_START_APP = 16;
    private static final int CTRL_RESET_VIDEO = 17;
    private static final int CTRL_RESIZE_DISPLAY = 21;

    private static final long POINTER_ID_GENERIC_FINGER = -2L;

    private final ShizukuBridge bridge;
    private final String packageName;
    private final Surface surface;
    private final Listener listener;
    private final int initialWidth;
    private final int initialHeight;
    private final int dpi;
    private final int bitRate;
    private final int maxFps;
    private final ExecutorService io = Executors.newFixedThreadPool(2);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object controlLock = new Object();

    private final int scid;
    private volatile int videoWidth;
    private volatile int videoHeight;
    private volatile OutputStream controlOut;
    private ParcelFileDescriptor videoPfd;
    private ParcelFileDescriptor controlPfd;
    private MediaCodec decoder;

    public ScrcpySession(ShizukuBridge bridge, String packageName, Surface surface,
                         int width, int height, int dpi, int bitRate, int maxFps,
                         Listener listener) {
        this.bridge = bridge;
        this.packageName = packageName;
        this.surface = surface;
        this.listener = listener;
        this.initialWidth = even(clamp(width, 320, 2800));
        this.initialHeight = even(clamp(height, 240, 2800));
        this.dpi = clamp(dpi, 120, 640);
        this.bitRate = clamp(bitRate, 1_000_000, 30_000_000);
        this.maxFps = clamp(maxFps, 15, 120);
        int candidate = (int) (System.nanoTime() ^ packageName.hashCode());
        candidate &= 0x7fff_ffff;
        this.scid = candidate == 0 ? 0x10203040 : candidate;
    }

    public int scid() { return scid; }
    public int videoWidth() { return videoWidth > 0 ? videoWidth : initialWidth; }
    public int videoHeight() { return videoHeight > 0 ? videoHeight : initialHeight; }

    public void start() {
        io.execute(this::runSession);
    }

    private void runSession() {
        try {
            state("Menjalankan scrcpy-server 4.1…");
            String start = bridge.startScrcpyServerBlocking(scid, initialWidth, initialHeight, dpi, bitRate, maxFps);
            if (start == null || !start.startsWith("OK|")) throw new IllegalStateException(start == null ? "start returned null" : start);

            state("Menghubungkan video…");
            videoPfd = bridge.connectScrcpyBlocking(scid);
            if (videoPfd == null) throw new IllegalStateException("video socket gagal terhubung · " + bridge.sessionInfoBlocking(scid));

            state("Menghubungkan kontrol…");
            controlPfd = bridge.connectScrcpyBlocking(scid);
            if (controlPfd == null) throw new IllegalStateException("control socket gagal terhubung · " + bridge.sessionInfoBlocking(scid));

            controlOut = new BufferedOutputStream(new FileOutputStream(controlPfd.getFileDescriptor()), 16 * 1024);
            try (InputStream input = new BufferedInputStream(new FileInputStream(videoPfd.getFileDescriptor()), 256 * 1024)) {
                int codecId = readInt(input);
                if (codecId == 0) throw new IllegalStateException("video stream dinonaktifkan oleh server");
                if (codecId == 1) throw new IllegalStateException("video encoder gagal dikonfigurasi");
                if (codecId != CODEC_H264) throw new IllegalStateException("codec bukan H.264: 0x" + Integer.toHexString(codecId));

                // scrcpy 4.1 sends a 12-byte *session packet* immediately after the codec id.
                // This is intentionally not parsed as a media packet: bit 63 marks a session,
                // bytes 4..7 are width and bytes 8..11 are height.
                byte[] firstHeader = new byte[STREAM_HEADER_SIZE];
                readFully(input, firstHeader, 0, STREAM_HEADER_SIZE);
                SessionHeader first = parseSessionHeader(firstHeader);
                videoWidth = first.width;
                videoHeight = first.height;
                configureDecoder(first.width, first.height);

                // Avoid a race where Android 16 assigns desktop windowing to the new display:
                // prepare it as a fullscreen app surface before START_APP is delivered.
                String displayPrep = bridge.prepareSessionDisplayBlocking(scid);
                state("Display siap · " + displayPrep);

                // The virtual display now exists and the scrcpy controller is attached to it.
                // Start the target app *inside* that display instead of using ActivityOptions on
                // the phone's normal task display area.
                // Prefix '+' is the official scrcpy force-stop-before-start form. This prevents
                // Android from reusing an existing task from the phone display and makes the target
                // start fresh inside this session's new virtual display.
                if (!sendStartApp("+" + packageName)) throw new IllegalStateException("START_APP gagal dikirim");
                if (listener != null) listener.onReady(first.width, first.height);
                state("Streaming · " + first.width + "×" + first.height + " · H.264");
                videoLoop(input);
            }
        } catch (Throwable t) {
            if (!closed.get()) fatal(t.getClass().getSimpleName() + ": " + safeMessage(t));
        } finally {
            closeInternal();
        }
    }

    private void configureDecoder(int width, int height) throws Exception {
        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        // Flex-display can change resolution at runtime.  Advertising a generous adaptive
        // playback envelope avoids unnecessary decoder recreation on many hardware codecs.
        format.setInteger(MediaFormat.KEY_MAX_WIDTH, 2800);
        format.setInteger(MediaFormat.KEY_MAX_HEIGHT, 2800);
        if (Build.VERSION.SDK_INT >= 30) {
            format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1);
        }
        MediaCodec codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        codec.configure(format, surface, null, 0);
        codec.start();
        decoder = codec;
    }

    private void videoLoop(InputStream input) throws Exception {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        byte[] header = new byte[STREAM_HEADER_SIZE];
        while (!closed.get()) {
            readFully(input, header, 0, STREAM_HEADER_SIZE);

            if (isSessionHeader(header)) {
                SessionHeader session = parseSessionHeader(header);
                boolean changed = session.width != videoWidth || session.height != videoHeight;
                videoWidth = session.width;
                videoHeight = session.height;
                if (changed && listener != null) listener.onVideoSizeChanged(session.width, session.height);
                continue;
            }

            long ptsFlags = readLong(header, 0);
            int size = readInt(header, 8);
            if (size <= 0 || size > 16 * 1024 * 1024) throw new IllegalStateException("invalid packet size " + size);
            byte[] packet = new byte[size];
            readFully(input, packet, 0, size);

            boolean config = (ptsFlags & FLAG_CONFIG) != 0;
            boolean key = (ptsFlags & FLAG_KEY) != 0;
            long pts = ptsFlags & PTS_MASK;

            queuePacket(packet, pts, config, key);
            drainDecoder(info);
        }
    }

    private static boolean isSessionHeader(byte[] header) {
        return header != null && header.length >= STREAM_HEADER_SIZE && (header[0] & 0x80) != 0;
    }

    private static SessionHeader parseSessionHeader(byte[] header) {
        if (!isSessionHeader(header)) throw new IllegalStateException("scrcpy: expected session packet");
        int width = readInt(header, 4);
        int height = readInt(header, 8);
        if (width < 1 || height < 1 || width > 8192 || height > 8192) {
            throw new IllegalStateException("scrcpy session size invalid: " + width + "x" + height);
        }
        boolean clientResized = (header[3] & 1) != 0;
        return new SessionHeader(width, height, clientResized);
    }

    private static final class SessionHeader {
        final int width;
        final int height;
        final boolean clientResized;
        SessionHeader(int width, int height, boolean clientResized) {
            this.width = width;
            this.height = height;
            this.clientResized = clientResized;
        }
    }

    private void queuePacket(byte[] packet, long pts, boolean config, boolean key) throws Exception {
        MediaCodec codec = decoder;
        if (codec == null) throw new IllegalStateException("decoder unavailable");
        int index;
        do {
            if (closed.get()) return;
            index = codec.dequeueInputBuffer(10_000);
            if (index < 0) drainDecoder(new MediaCodec.BufferInfo());
        } while (index < 0);

        ByteBuffer buffer = codec.getInputBuffer(index);
        if (buffer == null) throw new IllegalStateException("decoder input buffer null");
        buffer.clear();
        if (packet.length > buffer.remaining()) throw new IllegalStateException("packet bigger than decoder input buffer");
        buffer.put(packet);
        int flags = 0;
        if (config) flags |= MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
        if (key) flags |= MediaCodec.BUFFER_FLAG_KEY_FRAME;
        codec.queueInputBuffer(index, 0, packet.length, config ? 0 : Math.max(0, pts), flags);
    }

    private void drainDecoder(MediaCodec.BufferInfo info) {
        MediaCodec codec = decoder;
        if (codec == null) return;
        try {
            while (true) {
                int out = codec.dequeueOutputBuffer(info, 0);
                if (out >= 0) {
                    codec.releaseOutputBuffer(out, true);
                } else if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat f = codec.getOutputFormat();
                    int w = f.containsKey(MediaFormat.KEY_WIDTH) ? f.getInteger(MediaFormat.KEY_WIDTH) : videoWidth;
                    int h = f.containsKey(MediaFormat.KEY_HEIGHT) ? f.getInteger(MediaFormat.KEY_HEIGHT) : videoHeight;
                    if (w > 0 && h > 0 && (w != videoWidth || h != videoHeight)) {
                        videoWidth = w;
                        videoHeight = h;
                        if (listener != null) listener.onVideoSizeChanged(w, h);
                    }
                } else {
                    break;
                }
            }
        } catch (Throwable ignored) {
            // A fatal decoder error will normally surface while queuing the next packet.
        }
    }

    public boolean sendTouch(int action, float x, float y, int viewWidth, int viewHeight) {
        if (closed.get() || controlOut == null) return false;
        int sw = clamp(videoWidth(), 1, 65535);
        int sh = clamp(videoHeight(), 1, 65535);
        int px = clamp(Math.round(x * sw / Math.max(1f, viewWidth)), 0, sw - 1);
        int py = clamp(Math.round(y * sh / Math.max(1f, viewHeight)), 0, sh - 1);
        int androidAction;
        if (action == MotionEvent.ACTION_DOWN) androidAction = 0;
        else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) androidAction = 1;
        else if (action == MotionEvent.ACTION_MOVE) androidAction = 2;
        else return false;
        int pressure = androidAction == 1 ? 0 : 0xffff;

        ByteBuffer b = ByteBuffer.allocate(32);
        b.put((byte) CTRL_INJECT_TOUCH);
        b.put((byte) androidAction);
        b.putLong(POINTER_ID_GENERIC_FINGER);
        b.putInt(px);
        b.putInt(py);
        b.putShort((short) sw);
        b.putShort((short) sh);
        b.putShort((short) pressure);
        b.putInt(0); // action button
        b.putInt(0); // buttons
        return writeControl(b.array());
    }

    public boolean sendKey(int keyCode, int metaState) {
        if (closed.get() || controlOut == null) return false;
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return writeControl(new byte[]{(byte) CTRL_BACK_OR_SCREEN_ON, 0})
                    && writeControl(new byte[]{(byte) CTRL_BACK_OR_SCREEN_ON, 1});
        }
        ByteBuffer down = ByteBuffer.allocate(14);
        down.put((byte) CTRL_INJECT_KEYCODE).put((byte) 0).putInt(keyCode).putInt(0).putInt(metaState);
        ByteBuffer up = ByteBuffer.allocate(14);
        up.put((byte) CTRL_INJECT_KEYCODE).put((byte) 1).putInt(keyCode).putInt(0).putInt(metaState);
        return writeControl(down.array()) && writeControl(up.array());
    }

    public boolean resizeDisplay(int width, int height) {
        width = even(clamp(width, 320, 2800));
        height = even(clamp(height, 240, 2800));
        ByteBuffer b = ByteBuffer.allocate(5);
        b.put((byte) CTRL_RESIZE_DISPLAY);
        b.putShort((short) width);
        b.putShort((short) height);
        return writeControl(b.array());
    }

    public boolean resetVideo() {
        return writeControl(new byte[]{(byte) CTRL_RESET_VIDEO});
    }

    private boolean sendStartApp(String name) {
        if (name == null) return false;
        byte[] bytes = name.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length == 0 || bytes.length > 255) return false;
        byte[] msg = new byte[2 + bytes.length];
        msg[0] = (byte) CTRL_START_APP;
        msg[1] = (byte) bytes.length;
        System.arraycopy(bytes, 0, msg, 2, bytes.length);
        return writeControl(msg);
    }

    private boolean writeControl(byte[] data) {
        OutputStream out = controlOut;
        if (out == null || closed.get()) return false;
        synchronized (controlLock) {
            try {
                out.write(data);
                out.flush();
                return true;
            } catch (Throwable t) {
                if (!closed.get()) fatal("control: " + t.getClass().getSimpleName() + ": " + safeMessage(t));
                return false;
            }
        }
    }

    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        closeInternal();
    }

    private synchronized void closeInternal() {
        closed.set(true);
        MediaCodec codec = decoder;
        decoder = null;
        if (codec != null) {
            try { codec.stop(); } catch (Throwable ignored) {}
            try { codec.release(); } catch (Throwable ignored) {}
        }
        OutputStream out = controlOut;
        controlOut = null;
        if (out != null) try { out.close(); } catch (Throwable ignored) {}
        if (videoPfd != null) try { videoPfd.close(); } catch (Throwable ignored) {}
        if (controlPfd != null) try { controlPfd.close(); } catch (Throwable ignored) {}
        videoPfd = null;
        controlPfd = null;
        bridge.stopScrcpyServer(scid);
        io.shutdownNow();
    }

    private void state(String value) {
        if (listener != null && !closed.get()) listener.onState(value);
    }

    private void fatal(String value) {
        if (listener != null && !closed.get()) listener.onFatal(value + " · " + bridge.sessionInfoBlocking(scid));
    }

    private static int readInt(InputStream in) throws Exception {
        byte[] b = new byte[4];
        readFully(in, b, 0, 4);
        return readInt(b, 0);
    }

    private static int readInt(byte[] b, int off) {
        return ((b[off] & 0xff) << 24) | ((b[off + 1] & 0xff) << 16)
                | ((b[off + 2] & 0xff) << 8) | (b[off + 3] & 0xff);
    }

    private static long readLong(byte[] b, int off) {
        long v = 0;
        for (int i = 0; i < 8; i++) v = (v << 8) | (b[off + i] & 0xffL);
        return v;
    }

    private static void readFully(InputStream in, byte[] data, int off, int len) throws Exception {
        int total = 0;
        while (total < len) {
            int n = in.read(data, off + total, len - total);
            if (n < 0) throw new EOFException("stream closed");
            total += n;
        }
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    private static int even(int v) { return (v & 1) == 0 ? v : v - 1; }
    private static String safeMessage(Throwable t) { return t.getMessage() == null ? "no message" : t.getMessage(); }
}
