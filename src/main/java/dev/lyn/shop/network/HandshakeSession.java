package dev.lyn.shop.network;

import java.util.Objects;
import java.util.UUID;

/** One configuration-stage challenge. Time is monotonic and supplied for deterministic tests. */
public final class HandshakeSession {
    public static final long TIMEOUT_NANOS = 25_000_000_000L;
    public enum Result { ACCEPTED, NOT_STARTED, NONCE_MISMATCH, PROTOCOL_MISMATCH, VERSION_MISMATCH, EXPIRED, ALREADY_COMPLETE }
    private final int protocol;
    private final String version;
    private final UUID nonce;
    private boolean started;
    private boolean complete;
    private long deadline;
    public HandshakeSession(int protocol, String version, UUID nonce) {
        this.protocol = protocol; this.version = Objects.requireNonNull(version); this.nonce = Objects.requireNonNull(nonce);
    }
    public synchronized void start(long now) {
        if (started) throw new IllegalStateException("Handshake already started");
        started = true; deadline = now + TIMEOUT_NANOS;
    }
    public synchronized Result accept(int reportedProtocol, String reportedVersion, UUID reportedNonce, long now) {
        if (complete) return Result.ALREADY_COMPLETE;
        if (!started) return Result.NOT_STARTED;
        if (expired(now)) return Result.EXPIRED;
        if (!nonce.equals(reportedNonce)) return Result.NONCE_MISMATCH;
        if (reportedProtocol != protocol) return Result.PROTOCOL_MISMATCH;
        if (!version.equals(reportedVersion)) return Result.VERSION_MISMATCH;
        complete = true; return Result.ACCEPTED;
    }
    public synchronized boolean complete() { return complete; }
    public synchronized boolean expired(long now) { return started && !complete && now - deadline >= 0; }
    public UUID nonce() { return nonce; }
}
