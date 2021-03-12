package org.whispersystems.textsecuregcm.auth;

import javax.annotation.Nonnull;
import java.util.Objects;

public class DiskuvCredentials {
    @Nonnull
    private final String bearerToken;
    private final long deviceId;
    @Nonnull
    private final String devicePassword;

    public DiskuvCredentials(@Nonnull String bearerToken, long deviceId, @Nonnull String devicePassword) {
        this.bearerToken = bearerToken;
        this.deviceId = deviceId;
        this.devicePassword = devicePassword;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DiskuvCredentials that = (DiskuvCredentials) o;
        return deviceId == that.deviceId && bearerToken.equals(that.bearerToken) && devicePassword.equals(that.devicePassword);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bearerToken, deviceId, devicePassword);
    }

    @Override
    public String toString() {
        return "DiskuvCredentials{" +
                "bearerToken='" + bearerToken + '\'' +
                ", deviceId=" + deviceId +
                ", devicePassword='" + devicePassword + '\'' +
                '}';
    }

    public String getBearerToken() {
        return bearerToken;
    }

    public long getDeviceId() {
        return deviceId;
    }

    public String getDevicePassword() {
        return devicePassword;
    }
}
