package com.archer.ai;

import android.util.Base64;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.net.ssl.SSLSocketFactory;

// Minimal WebSocket client (RFC 6455) — enough for Gemini Live API.
public class WsClient {
    public interface Listener {
        void onText(String text);
        void onClose(String reason);
    }

    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private Listener listener;
    private volatile boolean open = false;
    private final SecureRandom random = new SecureRandom();

    public boolean connect(String host, String path, Listener listener) {
        this.listener = listener;
        try {
            socket = SSLSocketFactory.getDefault().createSocket(host, 443);
            socket.setSoTimeout(0);
            in = socket.getInputStream();
            out = socket.getOutputStream();

            byte[] keyBytes = new byte[16];
            random.nextBytes(keyBytes);
            String key = Base64.encodeToString(keyBytes, Base64.NO_WRAP);
            String req = "GET " + path + " HTTP/1.1\r\n"
                    + "Host: " + host + "\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: " + key + "\r\n"
                    + "Sec-WebSocket-Version: 13\r\n\r\n";
            out.write(req.getBytes(StandardCharsets.UTF_8));
            out.flush();

            BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String status = br.readLine();
            if (status == null || !status.contains("101")) {
                close();
                return false;
            }
            String line;
            while ((line = br.readLine()) != null && !line.isEmpty()) {
                // skip headers
            }
            open = true;
            new Thread(this::readLoop).start();
            return true;
        } catch (Exception e) {
            close();
            return false;
        }
    }

    public boolean isOpen() {
        return open;
    }

    public synchronized void sendText(String text) {
        if (!open) return;
        try {
            byte[] payload = text.getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream frame = new ByteArrayOutputStream();
            frame.write(0x81); // FIN + text
            int len = payload.length;
            if (len < 126) {
                frame.write(0x80 | len);
            } else if (len <= 0xFFFF) {
                frame.write(0x80 | 126);
                frame.write((len >> 8) & 0xFF);
                frame.write(len & 0xFF);
            } else {
                frame.write(0x80 | 127);
                for (int i = 7; i >= 0; i--) frame.write((int) ((len >> (8 * i)) & 0xFF));
            }
            byte[] mask = new byte[4];
            random.nextBytes(mask);
            frame.write(mask);
            for (int i = 0; i < len; i++) frame.write(payload[i] ^ mask[i % 4]);
            out.write(frame.toByteArray());
            out.flush();
        } catch (Exception e) {
            close();
        }
    }

    private void sendPong(byte[] payload) {
        try {
            ByteArrayOutputStream frame = new ByteArrayOutputStream();
            frame.write(0x8A); // FIN + pong
            frame.write(0x80 | payload.length);
            byte[] mask = new byte[4];
            random.nextBytes(mask);
            frame.write(mask);
            for (int i = 0; i < payload.length; i++) frame.write(payload[i] ^ mask[i % 4]);
            synchronized (this) {
                out.write(frame.toByteArray());
                out.flush();
            }
        } catch (Exception ignored) {
        }
    }

    private void readLoop() {
        ByteArrayOutputStream fragmented = null;
        try {
            while (open) {
                int b0 = in.read();
                if (b0 < 0) break;
                int b1 = in.read();
                if (b1 < 0) break;
                boolean fin = (b0 & 0x80) != 0;
                int opcode = b0 & 0x0F;
                boolean masked = (b1 & 0x80) != 0;
                long len = b1 & 0x7F;
                if (len == 126) {
                    len = ((long) in.read() << 8) | in.read();
                } else if (len == 127) {
                    len = 0;
                    for (int i = 0; i < 8; i++) len = (len << 8) | in.read();
                }
                byte[] mask = null;
                if (masked) {
                    mask = new byte[4];
                    readFully(mask);
                }
                byte[] payload = new byte[(int) len];
                readFully(payload);
                if (masked) {
                    for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];
                }
                switch (opcode) {
                    case 0x9: // ping
                        sendPong(payload);
                        break;
                    case 0x8: // close
                        open = false;
                        if (listener != null) listener.onClose("closed");
                        return;
                    case 0x1: // text
                    case 0x2: // binary
                        if (fin) {
                            if (listener != null) listener.onText(new String(payload, StandardCharsets.UTF_8));
                        } else {
                            fragmented = new ByteArrayOutputStream();
                            fragmented.write(payload);
                        }
                        break;
                    case 0x0: // continuation
                        if (fragmented != null) {
                            fragmented.write(payload);
                            if (fin) {
                                if (listener != null) listener.onText(fragmented.toString("UTF-8"));
                                fragmented = null;
                            }
                        }
                        break;
                }
            }
        } catch (Exception e) {
            // fall through to close
        }
        boolean wasOpen = open;
        open = false;
        if (wasOpen && listener != null) listener.onClose("disconnected");
    }

    private void readFully(byte[] buf) throws Exception {
        int off = 0;
        while (off < buf.length) {
            int r = in.read(buf, off, buf.length - off);
            if (r < 0) throw new Exception("EOF");
            off += r;
        }
    }

    public void close() {
        open = false;
        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {
        }
    }
}
