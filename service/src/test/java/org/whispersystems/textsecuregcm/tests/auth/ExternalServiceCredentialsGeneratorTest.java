package org.whispersystems.textsecuregcm.tests.auth;

import org.junit.Test;
import org.whispersystems.textsecuregcm.auth.ExternalServiceCredentialGenerator;
import org.whispersystems.textsecuregcm.auth.ExternalServiceCredentials;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.whispersystems.textsecuregcm.tests.util.UuidHelpers.UUID_ALICE_STRING;

public class ExternalServiceCredentialsGeneratorTest {

  @Test
  public void testGenerateDerivedUsername() {
    ExternalServiceCredentialGenerator generator = new ExternalServiceCredentialGenerator(new byte[32], new byte[32], true);
    ExternalServiceCredentials credentials = generator.generateFor(UUID_ALICE_STRING);

    assertThat(credentials.getUsername()).isNotEqualTo(UUID_ALICE_STRING);
    assertThat(credentials.getPassword().startsWith(UUID_ALICE_STRING)).isFalse();
  }

  @Test
  public void testGenerateNoDerivedUsername() {
    ExternalServiceCredentialGenerator generator = new ExternalServiceCredentialGenerator(new byte[32], new byte[32], false);
    ExternalServiceCredentials credentials = generator.generateFor(UUID_ALICE_STRING);

    assertThat(credentials.getUsername()).isEqualTo(UUID_ALICE_STRING);
    assertThat(credentials.getPassword().startsWith(UUID_ALICE_STRING)).isTrue();
  }

}
