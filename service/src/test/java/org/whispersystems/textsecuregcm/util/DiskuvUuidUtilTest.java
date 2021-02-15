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
    public void givenUsaNumber__whenUuidForE164Number__thenUUID4() {
        // given: USA number
        String e164Number = "+15555555555";
        // when
        UUID uuid = DiskuvUuidUtil.uuidForE164Number(e164Number);
        // then: UUID version 4
        assertThat(uuid.version()).isEqualTo(4);
        // then: variant 1 (IETF variant, which is 10 in binary)
        assertThat(uuid.variant()).isEqualTo(0b10);
        // then: Diskuv UUID version 0
        assertThat(uuid.getMostSignificantBits() & 0xc0000000_00000000L).isEqualTo(0);
    }

    @Test
    public void givenUkNumber__whenUuidForE164Number__thenUUID4() {
        // given: UK number
        String e164Number = "+442079460018";
        // when
        UUID uuid = DiskuvUuidUtil.uuidForE164Number(e164Number);
        // then: UUID version 4
        assertThat(uuid.version()).isEqualTo(4);
        // then: variant 1 (IETF variant, which is 10 in binary)
        assertThat(uuid.variant()).isEqualTo(0b10);
        // then: Diskuv UUID version 0
        assertThat(uuid.getMostSignificantBits() & 0xc0000000_00000000L).isEqualTo(0);
    }

    @Test
    public void givenObviouslyInvalidE164Number__whenUuidForE164Number__thenFail() {
        // given: obviously invalid e164
        String e164Number = "12345";
        // when / then
        exceptionRule.expect(IllegalArgumentException.class);
        DiskuvUuidUtil.uuidForE164Number(e164Number);
    }
}