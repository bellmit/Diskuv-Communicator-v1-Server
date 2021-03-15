package org.whispersystems.textsecuregcm.auth;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Objects;

public class DiskuvCredentials {
    @Nonnull
    private final String bearerToken;
    private final long deviceId;
    @Nonnull
    private final byte[] devicePassword;

    public DiskuvCredentials(@Nonnull String bearerToken, long deviceId, @Nonnull byte[] devicePassword) {
        this.bearerToken = bearerToken;
        this.deviceId = deviceId;
        this.devicePassword = devicePassword;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DiskuvCredentials that = (DiskuvCredentials) o;
        return deviceId == that.deviceId && bearerToken.equals(that.bearerToken) && Arrays.equals(devicePassword,
                                                                                                  that.devicePassword);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(bearerToken, deviceId);
        result = 31 * result + Arrays.hashCode(devicePassword);
        return result;
    }

    @Override
    public String toString() {
        return "DiskuvCredentials{" +
                "bearerToken='" + bearerToken + '\'' +
                ", deviceId=" + deviceId +
                ", devicePassword=" + Arrays.toString(devicePassword) +
                '}';
    }

    public String getBearerToken() {
        return bearerToken;
    }

    public long getDeviceId() {
        return deviceId;
    }

    public byte[] getDevicePassword() {
        return devicePassword;
    }
}
