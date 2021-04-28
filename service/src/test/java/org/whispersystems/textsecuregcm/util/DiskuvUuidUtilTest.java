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
    public void givenTypicalEmailAddress__whenVerifyAndNormalizeEmailAddress__thenOk() {
        // given
        String emailAddress = "trees@yahoo.com";
        // when / then ok
        DiskuvUuidUtil.verifyEmailAddress(emailAddress);
    }

    @Test
    public void givenQuotedEmailAddress__whenVerifyAndNormalizeEmailAddress__thenOk() {
        // given
        String emailAddress = "\"trees@are@all@right\"@yahoo.com";
        // when / then ok
        DiskuvUuidUtil.verifyEmailAddress(emailAddress);
    }

    @Test
    public void givenQuotedEmailAddressWithNoDotInDomain__whenVerifyAndNormalizeEmailAddress__thenFail() {
        // given
        String emailAddress = "\"trees@are@all@right\"@yahoo";
        // when / then fail
        exceptionRule.expect(IllegalArgumentException.class);
        DiskuvUuidUtil.verifyEmailAddress(emailAddress);
    }

    @Test
    public void givenInternationalAddress__whenVerifyAndNormalizeEmailAddress__thenOk() {
        // given
        String emailAddress = "\u03C7\u03C1\u03AE\u03C3\u03C4\u03B7\u03C2@\u03C0\u03B1\u03C1\u03AC\u03B4\u03B5\u03B9\u03B3\u03BC\u03B1.\u03B5\u03BB";
        // when / then ok
        DiskuvUuidUtil.verifyEmailAddress(emailAddress);
    }

    @Test
    public void givenInternationalizedDomainName__whenVerifyAndNormalizeEmailAddress__thenOk() {
        // given
        String emailAddress = "trees@xn--mller-brombel-rmb4fg.de";
        // when / then ok
        DiskuvUuidUtil.verifyEmailAddress(emailAddress);
    }

    @Test
    public void givenUucpEmailAddress__whenVerifyAndNormalizeEmailAddress__thenFail() {
        // given
        String emailAddress = "bob!alice@example.org";
        // when / then fail
        exceptionRule.expect(IllegalArgumentException.class);
        DiskuvUuidUtil.verifyEmailAddress(emailAddress);
    }

    @Test
    public void givenQuotedEmailAddressWithSpace__whenVerifyAndNormalizeEmailAddress__thenFail() {
        // given
        String emailAddress = "\"alice bob\"@example.org";
        // when / then fail
        exceptionRule.expect(IllegalArgumentException.class);
        DiskuvUuidUtil.verifyEmailAddress(emailAddress);
    }

    @Test
    public void givenEmailAddress__whenUuidForOutdoorEmailAddress__thenUUID4() {
        // given: email address
        String emailAddress = "trees@yahoo.com";
        // when
        UUID uuid = DiskuvUuidUtil.uuidForOutdoorEmailAddress(emailAddress);
        // then: UUID version 4
        assertThat(uuid.version()).isEqualTo(4);
        // then: variant 1 (IETF / RFC 4122 variant, which is 10 in binary)
        assertThat(uuid.variant()).isEqualTo(0b10);
        // then: Diskuv UUID type 0
        assertThat(uuid.getMostSignificantBits() & 0xc0000000_00000000L).isEqualTo(0x00000000_00000000L);
        // then: verified UUID
        DiskuvUuidType type = DiskuvUuidUtil.verifyDiskuvUuid(uuid.toString());
        assertThat(type).isEqualTo(DiskuvUuidType.OUTDOORS);
        // then: hex constant
        assertThat(uuid.toString()).isEqualTo("3c5437da-d904-497b-a61c-9227dbbad7e5");
    }

    @Test
    public void givenEmailAddressAndSanctuaryGroupMasterKey__whenUuidForSanctuaryEmailAddress__thenUUID4() {
        // given: email address
        String emailAddress            = "trees@yahoo.com";
        byte[] sanctuaryGroupMasterKey = new byte[32];
        // when
        UUID uuid = DiskuvUuidUtil.uuidForSanctuaryEmailAddress(emailAddress, sanctuaryGroupMasterKey);
        // then: UUID version 4
        assertThat(uuid.version()).isEqualTo(4);
        // then: variant 1 (IETF / RFC 4122 variant, which is 10 in binary)
        assertThat(uuid.variant()).isEqualTo(0b10);
        // then: Diskuv UUID type 1
        assertThat(uuid.getMostSignificantBits() & 0xc0000000_00000000L).isEqualTo(0x40000000_00000000L);
        // then: verified UUID
        DiskuvUuidType type = DiskuvUuidUtil.verifyDiskuvUuid(uuid.toString());
        assertThat(type).isEqualTo(DiskuvUuidType.SANCTUARY_SPECIFIC);
        // then: different from outdoors uuid
        assertThat(uuid).isNotEqualTo(DiskuvUuidUtil.uuidForOutdoorEmailAddress(emailAddress));
        // then: hex constant
        assertThat(uuid.toString()).isEqualTo("45e1c7de-d47d-49d3-8352-e4cfa24b166e");
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