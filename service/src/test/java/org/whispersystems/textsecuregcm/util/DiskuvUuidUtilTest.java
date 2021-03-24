package org.whispersystems.textsecuregcm.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class DiskuvUuidUtilTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void givenEmailAddress__whenUuidForOutdoorEmailAddress__thenUUID4() {
        // given: email address
        String emailAddress = "trees@yahoo.com";
        // when
        UUID uuid = DiskuvUuidUtil.uuidForOutdoorEmailAddress(emailAddress);
        // then: UUID version 4
        assertThat(uuid.version()).isEqualTo(4);
        // then: variant 1 (IETF variant, which is 10 in binary)
        assertThat(uuid.variant()).isEqualTo(0b10);
        // then: Diskuv UUID type 0
        assertThat(uuid.getMostSignificantBits() & 0xc0000000_00000000L).isEqualTo(0x00000000_00000000L);
        // then: verified UUID
        DiskuvUuidType type = DiskuvUuidUtil.verifyDiskuvUuid(uuid.toString());
        assertThat(type).isEqualTo(DiskuvUuidType.OUTDOORS);
    }

    @Test
    public void givenEmailAddressAndSanctuaryToken__whenUuidForSanctuaryEmailAddress__thenUUID4() {
        // given: email address
        String emailAddress = "trees@yahoo.com";
        byte[] sanctuaryToken = new byte[32];
        // when
        UUID   uuid       = DiskuvUuidUtil.uuidForSanctuaryEmailAddress(emailAddress, sanctuaryToken);
        // then: UUID version 4
        assertThat(uuid.version()).isEqualTo(4);
        // then: variant 1 (IETF variant, which is 10 in binary)
        assertThat(uuid.variant()).isEqualTo(0b10);
        // then: Diskuv UUID type 1
        assertThat(uuid.getMostSignificantBits() & 0xc0000000_00000000L).isEqualTo(0x40000000_00000000L);
        // then: verified UUID
        DiskuvUuidType type = DiskuvUuidUtil.verifyDiskuvUuid(uuid.toString());
        assertThat(type).isEqualTo(DiskuvUuidType.HOUSE_SPECIFIC);
        // then: different from outdoors uuid
        assertThat(uuid).isNotEqualTo(DiskuvUuidUtil.uuidForOutdoorEmailAddress(emailAddress));
    }

    @Test
    public void givenObviouslyInvalidEmailAddress__whenVerifyDiskuvUuid__thenFail() {
        // given: obviously invalid email address
        String emailAddress = "12345";
        // when / then
        exceptionRule.expect(IllegalArgumentException.class);
        DiskuvUuidUtil.verifyDiskuvUuid(emailAddress);
    }

    @Test
    public void givenObviouslyInvalidEmailAddress__whenUuidForOutdoorEmailAddress__thenFail() {
        // given: obviously invalid email address
        String emailAddress = "12345";
        // when / then
        exceptionRule.expect(IllegalArgumentException.class);
        DiskuvUuidUtil.uuidForOutdoorEmailAddress(emailAddress);
    }
}