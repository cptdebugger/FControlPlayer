package me.morok.fControlPlayer.protocol.compat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum ProtocolLibVersion {
    V4_6_0(4, 6, 0),
    V4_7_0(4, 7, 0),
    V4_8_0(4, 8, 0),
    V5_0_0(5, 0, 0),
    V5_1_0(5, 1, 0),
    V5_2_0(5, 2, 0),
    V5_3_0(5, 3, 0),
    V5_4_0(5, 4, 0);

    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");

    private final int major;
    private final int minor;
    private final int patch;

    ProtocolLibVersion(int major, int minor, int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    public static ProtocolLibVersion parse(String rawVersion) {
        if (rawVersion == null || rawVersion.isEmpty()) {
            return null;
        }
        Matcher matcher = VERSION_PATTERN.matcher(rawVersion);
        if (!matcher.find()) {
            return null;
        }
        int major = parseInt(matcher.group(1));
        int minor = parseInt(matcher.group(2));
        int patch = parseInt(matcher.group(3));
        for (ProtocolLibVersion version : values()) {
            if (version.major == major && version.minor == minor && version.patch == patch) {
                return version;
            }
        }
        return null;
    }

    public String displayName() {
        return major + "." + minor + "." + patch;
    }

    public int getMajor() {
        return major;
    }

    @Override
    public String toString() {
        return displayName();
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}
