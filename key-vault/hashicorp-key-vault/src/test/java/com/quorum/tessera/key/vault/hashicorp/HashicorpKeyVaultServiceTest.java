package com.quorum.tessera.key.vault.hashicorp;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.quorum.tessera.key.vault.SetSecretResponse;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.VaultTransitTemplate;
import org.springframework.vault.core.VaultVersionedKeyValueTemplate;
import org.springframework.vault.support.Versioned;

public class HashicorpKeyVaultServiceTest {

  private HashicorpKeyVaultService keyVaultService;

  private VaultOperations vaultOperations;

  private VaultVersionedKeyValueTemplateFactory vaultVersionedKeyValueTemplateFactory;

  private HashicorpTransitSecretEngineService hashicorpTransitSecretEngineService;

  @Before
  public void beforeTest() {
    this.vaultOperations = mock(VaultOperations.class);
    this.vaultVersionedKeyValueTemplateFactory = mock(VaultVersionedKeyValueTemplateFactory.class);
    this.keyVaultService =
        new HashicorpKeyVaultService(vaultOperations, () -> vaultVersionedKeyValueTemplateFactory);
  }

  @After
  public void afterTest() {
    verifyNoMoreInteractions(vaultOperations);
    verifyNoMoreInteractions(vaultVersionedKeyValueTemplateFactory);
  }

  @Test
  public void getSecret() {
    final Map<String, String> getSecretData =
        Map.of(
            HashicorpKeyVaultService.SECRET_ENGINE_NAME_KEY, "secretEngine",
            HashicorpKeyVaultService.SECRET_NAME_KEY, "secretName",
            HashicorpKeyVaultService.SECRET_ID_KEY, "keyId");

    Versioned versionedResponse = mock(Versioned.class);

    when(versionedResponse.hasData()).thenReturn(true);

    VaultVersionedKeyValueTemplate vaultVersionedKeyValueTemplate =
        mock(VaultVersionedKeyValueTemplate.class);
    when(vaultVersionedKeyValueTemplate.get("secretName", Versioned.Version.from(0)))
        .thenReturn(versionedResponse);

    when(vaultVersionedKeyValueTemplateFactory.createVaultVersionedKeyValueTemplate(
            vaultOperations, "secretEngine"))
        .thenReturn(vaultVersionedKeyValueTemplate);

    String keyValue = "keyvalue";
    Map responseData = Map.of("keyId", keyValue);
    when(versionedResponse.getData()).thenReturn(responseData);

    String result = keyVaultService.getSecret(getSecretData);
    assertThat(result).isEqualTo(keyValue);

    verify(vaultVersionedKeyValueTemplateFactory)
        .createVaultVersionedKeyValueTemplate(vaultOperations, "secretEngine");
  }

  @Test
  public void getSecretThrowsExceptionIfNullRetrievedFromVault() {

    Map<String, String> getSecretData =
        Map.of(
            HashicorpKeyVaultService.SECRET_ENGINE_NAME_KEY, "engine",
            HashicorpKeyVaultService.SECRET_NAME_KEY, "secretName",
            HashicorpKeyVaultService.SECRET_ID_KEY, "id",
            HashicorpKeyVaultService.SECRET_VERSION_KEY, "0");

    VaultVersionedKeyValueTemplate vaultVersionedKeyValueTemplate =
        mock(VaultVersionedKeyValueTemplate.class);
    when(vaultVersionedKeyValueTemplate.get("secretName", Versioned.Version.from(0)))
        .thenReturn(null);

    when(vaultVersionedKeyValueTemplateFactory.createVaultVersionedKeyValueTemplate(
            vaultOperations, "engine"))
        .thenReturn(vaultVersionedKeyValueTemplate);

    Throwable ex = catchThrowable(() -> keyVaultService.getSecret(getSecretData));

    assertThat(ex).isExactlyInstanceOf(HashicorpVaultException.class);
    assertThat(ex).hasMessage("No data found at engine/secretName");

    verify(vaultVersionedKeyValueTemplateFactory)
        .createVaultVersionedKeyValueTemplate(vaultOperations, "engine");
  }

  @Test
  public void getSecretThrowsExceptionIfNoDataRetrievedFromVault() {

    final Map<String, String> getSecretData =
        Map.of(
            HashicorpKeyVaultService.SECRET_ENGINE_NAME_KEY, "engine",
            HashicorpKeyVaultService.SECRET_NAME_KEY, "secretName",
            HashicorpKeyVaultService.SECRET_ID_KEY, "id",
            HashicorpKeyVaultService.SECRET_VERSION_KEY, "0");

    Versioned versionedResponse = mock(Versioned.class);
    when(versionedResponse.hasData()).thenReturn(false);

    VaultVersionedKeyValueTemplate vaultVersionedKeyValueTemplate =
        mock(VaultVersionedKeyValueTemplate.class);
    when(vaultVersionedKeyValueTemplate.get("secretName", Versioned.Version.from(0)))
        .thenReturn(versionedResponse);

    when(vaultVersionedKeyValueTemplateFactory.createVaultVersionedKeyValueTemplate(
            vaultOperations, "engine"))
        .thenReturn(vaultVersionedKeyValueTemplate);

    Throwable ex = catchThrowable(() -> keyVaultService.getSecret(getSecretData));

    assertThat(ex).isExactlyInstanceOf(HashicorpVaultException.class);
    assertThat(ex).hasMessage("No data found at engine/secretName");

    verify(vaultVersionedKeyValueTemplateFactory)
        .createVaultVersionedKeyValueTemplate(vaultOperations, "engine");
  }

  @Test
  public void getSecretThrowsExceptionIfValueNotFoundForGivenId() {

    final Map<String, String> getSecretData =
        Map.of(
            HashicorpKeyVaultService.SECRET_ENGINE_NAME_KEY, "engine",
            HashicorpKeyVaultService.SECRET_NAME_KEY, "secretName",
            HashicorpKeyVaultService.SECRET_ID_KEY, "id",
            HashicorpKeyVaultService.SECRET_VERSION_KEY, "0");

    Versioned versionedResponse = mock(Versioned.class);
    when(versionedResponse.hasData()).thenReturn(true);

    VaultVersionedKeyValueTemplate vaultVersionedKeyValueTemplate =
        mock(VaultVersionedKeyValueTemplate.class);
    when(vaultVersionedKeyValueTemplate.get("secretName", Versioned.Version.from(0)))
        .thenReturn(versionedResponse);

    when(vaultVersionedKeyValueTemplateFactory.createVaultVersionedKeyValueTemplate(
            vaultOperations, "engine"))
        .thenReturn(vaultVersionedKeyValueTemplate);

    Map responseData = Map.of();
    when(versionedResponse.getData()).thenReturn(responseData);

    Throwable ex = catchThrowable(() -> keyVaultService.getSecret(getSecretData));

    assertThat(ex).isExactlyInstanceOf(HashicorpVaultException.class);
    assertThat(ex).hasMessage("No value with id id found at engine/secretName");

    verify(vaultVersionedKeyValueTemplateFactory)
        .createVaultVersionedKeyValueTemplate(vaultOperations, "engine");
  }

  @Test
  public void setSecretReturnsMetadataObject() {
    Map<String, String> setSecretData =
        Map.of(
            HashicorpKeyVaultService.SECRET_ENGINE_NAME_KEY, "engine",
            HashicorpKeyVaultService.SECRET_NAME_KEY, "name");

    Versioned.Metadata metadata = mock(Versioned.Metadata.class);
    VaultVersionedKeyValueTemplate vaultVersionedKeyValueTemplate =
        mock(VaultVersionedKeyValueTemplate.class);
    when(vaultVersionedKeyValueTemplate.put(eq("name"), anyMap())).thenReturn(metadata);

    when(vaultVersionedKeyValueTemplateFactory.createVaultVersionedKeyValueTemplate(
            vaultOperations, "engine"))
        .thenReturn(vaultVersionedKeyValueTemplate);

    Versioned.Version version = mock(Versioned.Version.class);
    when(version.getVersion()).thenReturn(22);

    when(metadata.getVersion()).thenReturn(version);

    SetSecretResponse result = keyVaultService.setSecret(setSecretData);

    assertThat(result.getProperties()).size().isEqualTo(1);
    assertThat(result.getProperties()).contains(entry("version", "22"));

    verify(vaultVersionedKeyValueTemplateFactory)
        .createVaultVersionedKeyValueTemplate(vaultOperations, "engine");
  }

  @Test
  public void setSecretIfNullPointerExceptionThenHashicorpExceptionThrown() {
    Map<String, String> setSecretData =
        Map.of(
            HashicorpKeyVaultService.SECRET_NAME_KEY, "SomeName",
            HashicorpKeyVaultService.SECRET_ENGINE_NAME_KEY, "SomeEngineName");

    when(vaultVersionedKeyValueTemplateFactory.createVaultVersionedKeyValueTemplate(
            vaultOperations, "SomeEngineName"))
        .thenReturn(null);

    Throwable ex = catchThrowable(() -> keyVaultService.setSecret(setSecretData));

    assertThat(ex).isExactlyInstanceOf(HashicorpVaultException.class);
    assertThat(ex.getMessage())
        .isEqualTo(
            "Unable to save generated secret to vault.  Ensure that the secret engine being used is a v2 kv secret engine");

    verify(vaultVersionedKeyValueTemplateFactory)
        .createVaultVersionedKeyValueTemplate(vaultOperations, "SomeEngineName");
  }

  @Test
  public void
      givenTransitSecretEngineConfigurationAreAvailableAndKVDataWasEncryptedByTseThenKVDataAreDecryptedByTseFirstBeforeReturningIt() {
    String encryptedKeyValue = "vault:v1:encrypted value";
    String decryptedKeyValue = "decrypted data";

    var mockedVaultTransitTemplate = mock(VaultTransitTemplate.class);
    when(mockedVaultTransitTemplate.decrypt(anyString(), anyString()))
        .thenReturn(decryptedKeyValue);

    this.hashicorpTransitSecretEngineService =
        spy(new HashicorpTransitSecretEngineService(vaultOperations));
    doReturn(mockedVaultTransitTemplate)
        .when(this.hashicorpTransitSecretEngineService)
        .createVaultTransitTemplate(anyString());

    var keyVaultUsingTseService =
        new HashicorpKeyVaultService(
            vaultOperations,
            () -> vaultVersionedKeyValueTemplateFactory,
            (v) -> this.hashicorpTransitSecretEngineService);

    Map<String, String> getSecretData =
        Map.of(
            HashicorpKeyVaultService.SECRET_ENGINE_NAME_KEY, "secretEngine",
            HashicorpKeyVaultService.SECRET_NAME_KEY, "secretName",
            HashicorpKeyVaultService.SECRET_ID_KEY, "keyId",
            HashicorpKeyVaultService.TRANSIT_SECRET_ENGINE_NAME_KEY, "transit",
            HashicorpKeyVaultService.TRANSIT_KEY_NAME_KEY, "my-key");

    Versioned versionedResponse = mock(Versioned.class);

    when(versionedResponse.hasData()).thenReturn(true);

    VaultVersionedKeyValueTemplate vaultVersionedKeyValueTemplate =
        mock(VaultVersionedKeyValueTemplate.class);
    when(vaultVersionedKeyValueTemplate.get("secretName", Versioned.Version.from(0)))
        .thenReturn(versionedResponse);

    when(vaultVersionedKeyValueTemplateFactory.createVaultVersionedKeyValueTemplate(
            vaultOperations, "secretEngine"))
        .thenReturn(vaultVersionedKeyValueTemplate);

    Map responseData = Map.of("keyId", encryptedKeyValue);
    when(versionedResponse.getData()).thenReturn(responseData);

    String result = keyVaultUsingTseService.getSecret(getSecretData);
    assertThat(result).isEqualTo(decryptedKeyValue);

    verify(vaultVersionedKeyValueTemplateFactory)
        .createVaultVersionedKeyValueTemplate(vaultOperations, "secretEngine");
  }

  @Test
  public void
      givenKVDataWasEncryptedByTseButTransitSecretEngineConfigurationAreMissingThenShouldThrowHashicorpVaultException() {
    String encryptedKeyValue = "vault:v1:encrypted value";
    String decryptedKeyValue = "decrypted data";

    var mockedVaultTransitTemplate = mock(VaultTransitTemplate.class);
    when(mockedVaultTransitTemplate.decrypt(anyString(), anyString()))
        .thenReturn(decryptedKeyValue);

    this.hashicorpTransitSecretEngineService =
        spy(new HashicorpTransitSecretEngineService(vaultOperations));
    doReturn(mockedVaultTransitTemplate)
        .when(this.hashicorpTransitSecretEngineService)
        .createVaultTransitTemplate(anyString());

    var keyVaultUsingTseService =
        new HashicorpKeyVaultService(
            vaultOperations,
            () -> vaultVersionedKeyValueTemplateFactory,
            (v) -> this.hashicorpTransitSecretEngineService);

    Map<String, String> getSecretData =
        Map.of(
            HashicorpKeyVaultService.SECRET_ENGINE_NAME_KEY, "secretEngine",
            HashicorpKeyVaultService.SECRET_NAME_KEY, "secretName",
            HashicorpKeyVaultService.SECRET_ID_KEY, "keyId");

    Versioned versionedResponse = mock(Versioned.class);

    when(versionedResponse.hasData()).thenReturn(true);

    VaultVersionedKeyValueTemplate vaultVersionedKeyValueTemplate =
        mock(VaultVersionedKeyValueTemplate.class);
    when(vaultVersionedKeyValueTemplate.get("secretName", Versioned.Version.from(0)))
        .thenReturn(versionedResponse);

    when(vaultVersionedKeyValueTemplateFactory.createVaultVersionedKeyValueTemplate(
            vaultOperations, "secretEngine"))
        .thenReturn(vaultVersionedKeyValueTemplate);

    Map responseData = Map.of("keyId", encryptedKeyValue);
    when(versionedResponse.getData()).thenReturn(responseData);

    Throwable ex = catchThrowable(() -> keyVaultUsingTseService.getSecret(getSecretData));

    assertThat(ex).isExactlyInstanceOf(HashicorpVaultException.class);
    assertThat(ex)
        .hasMessage(
            "Vault key value needs to be decrypted, but the following configuration was/were not provided: [ transitSecretEngineName, transitKeyName ]");

    verify(vaultVersionedKeyValueTemplateFactory)
        .createVaultVersionedKeyValueTemplate(vaultOperations, "secretEngine");
  }

  @Test
  public void
      givenTransitSecretEngineConfigurationAreAvailableThenPrivateAndPublicKeyShouldBeTseEncryptedBeforeStoredInKV() {
    String plainTextPublicKey = "plain text public key";
    String plainTextPrivateKey = "plain text private key";
    String encryptedPublicKey = "encrypted public key";
    String encryptedPrivateKey = "encrypted private key";
    String transitKeyName = "my-key";

    var mockedVaultTransitTemplate = mock(VaultTransitTemplate.class);
    when(mockedVaultTransitTemplate.encrypt(transitKeyName, plainTextPublicKey))
        .thenReturn(encryptedPublicKey);
    when(mockedVaultTransitTemplate.encrypt(transitKeyName, plainTextPrivateKey))
        .thenReturn(encryptedPrivateKey);

    this.hashicorpTransitSecretEngineService =
        spy(new HashicorpTransitSecretEngineService(vaultOperations));
    doReturn(mockedVaultTransitTemplate)
        .when(this.hashicorpTransitSecretEngineService)
        .createVaultTransitTemplate(anyString());

    var keyVaultUsingTseService =
        new HashicorpKeyVaultService(
            vaultOperations,
            () -> vaultVersionedKeyValueTemplateFactory,
            (v) -> this.hashicorpTransitSecretEngineService);

    Map<String, String> setSecretData =
        Map.of(
            HashicorpKeyVaultService.SECRET_ENGINE_NAME_KEY,
            "engine",
            HashicorpKeyVaultService.SECRET_NAME_KEY,
            "name",
            HashicorpKeyVaultService.TRANSIT_SECRET_ENGINE_NAME_KEY,
            "transit",
            HashicorpKeyVaultService.TRANSIT_KEY_NAME_KEY,
            transitKeyName,
            "publicKey",
            plainTextPublicKey,
            "privateKey",
            plainTextPrivateKey);

    Versioned.Metadata metadata = mock(Versioned.Metadata.class);
    VaultVersionedKeyValueTemplate vaultVersionedKeyValueTemplate =
        mock(VaultVersionedKeyValueTemplate.class);
    when(vaultVersionedKeyValueTemplate.put(eq("name"), anyMap())).thenReturn(metadata);

    when(vaultVersionedKeyValueTemplateFactory.createVaultVersionedKeyValueTemplate(
            vaultOperations, "engine"))
        .thenReturn(vaultVersionedKeyValueTemplate);

    Versioned.Version version = mock(Versioned.Version.class);
    when(version.getVersion()).thenReturn(22);

    when(metadata.getVersion()).thenReturn(version);

    SetSecretResponse result = keyVaultUsingTseService.setSecret(setSecretData);

    assertThat(result.getProperties()).size().isEqualTo(1);
    assertThat(result.getProperties()).contains(entry("version", "22"));

    var argCapture = ArgumentCaptor.forClass(Object.class);
    verify(vaultVersionedKeyValueTemplate).put(eq("name"), argCapture.capture());
    var capturedArgument = (Map<String, String>) argCapture.getValue();

    assertThat(capturedArgument.get("publicKey")).isEqualTo(encryptedPublicKey);
    assertThat(capturedArgument.get("privateKey")).isEqualTo(encryptedPrivateKey);

    verify(vaultVersionedKeyValueTemplateFactory)
        .createVaultVersionedKeyValueTemplate(vaultOperations, "engine");
  }

  @Test
  public void
      givenTransitSecretEngineConfigurationAreMissingThenPrivateAndPublicKeyShouldBeStoredInKvWithoutTseEncyption() {
    String plainTextPublicKey = "plain text public key";
    String plainTextPrivateKey = "plain text private key";
    String encryptedPublicKey = "encrypted public key";
    String encryptedPrivateKey = "encrypted private key";
    String transitKeyName = "my-key";

    var mockedVaultTransitTemplate = mock(VaultTransitTemplate.class);
    when(mockedVaultTransitTemplate.encrypt(transitKeyName, plainTextPublicKey))
        .thenReturn(encryptedPublicKey);
    when(mockedVaultTransitTemplate.encrypt(transitKeyName, plainTextPrivateKey))
        .thenReturn(encryptedPrivateKey);

    this.hashicorpTransitSecretEngineService =
        spy(new HashicorpTransitSecretEngineService(vaultOperations));
    doReturn(mockedVaultTransitTemplate)
        .when(this.hashicorpTransitSecretEngineService)
        .createVaultTransitTemplate(anyString());

    var keyVaultUsingTseService =
        new HashicorpKeyVaultService(
            vaultOperations,
            () -> vaultVersionedKeyValueTemplateFactory,
            (v) -> this.hashicorpTransitSecretEngineService);

    Map<String, String> setSecretData =
        Map.of(
            HashicorpKeyVaultService.SECRET_ENGINE_NAME_KEY,
            "engine",
            HashicorpKeyVaultService.SECRET_NAME_KEY,
            "name",
            HashicorpKeyVaultService.TRANSIT_KEY_NAME_KEY,
            transitKeyName,
            "publicKey",
            plainTextPublicKey,
            "privateKey",
            plainTextPrivateKey);

    Versioned.Metadata metadata = mock(Versioned.Metadata.class);
    VaultVersionedKeyValueTemplate vaultVersionedKeyValueTemplate =
        mock(VaultVersionedKeyValueTemplate.class);
    when(vaultVersionedKeyValueTemplate.put(eq("name"), anyMap())).thenReturn(metadata);

    when(vaultVersionedKeyValueTemplateFactory.createVaultVersionedKeyValueTemplate(
            vaultOperations, "engine"))
        .thenReturn(vaultVersionedKeyValueTemplate);

    Versioned.Version version = mock(Versioned.Version.class);
    when(version.getVersion()).thenReturn(22);

    when(metadata.getVersion()).thenReturn(version);

    SetSecretResponse result = keyVaultUsingTseService.setSecret(setSecretData);

    assertThat(result.getProperties()).size().isEqualTo(1);
    assertThat(result.getProperties()).contains(entry("version", "22"));

    var argCapture = ArgumentCaptor.forClass(Object.class);
    verify(vaultVersionedKeyValueTemplate).put(eq("name"), argCapture.capture());
    var capturedArgument = (Map<String, String>) argCapture.getValue();

    assertThat(capturedArgument.get("publicKey")).isEqualTo(plainTextPublicKey);
    assertThat(capturedArgument.get("privateKey")).isEqualTo(plainTextPrivateKey);

    verify(vaultVersionedKeyValueTemplateFactory)
        .createVaultVersionedKeyValueTemplate(vaultOperations, "engine");
  }
}
