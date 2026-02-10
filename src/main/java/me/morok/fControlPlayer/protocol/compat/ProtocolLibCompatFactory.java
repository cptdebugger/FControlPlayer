package me.morok.fControlPlayer.protocol.compat;

public final class ProtocolLibCompatFactory {
    private ProtocolLibCompatFactory() {
    }

    public static ProtocolLibCompat create(ProtocolLibVersion version) {
        if (version == null) {
            throw new IllegalArgumentException("ProtocolLib version must not be null.");
        }
        switch (version.getMajor()) {
            case 4:
                return new ProtocolLibCompatV4();
            case 5:
                return new ProtocolLibCompatV5();
            default:
                throw new IllegalArgumentException("Unsupported ProtocolLib version: " + version);
        }
    }
}
