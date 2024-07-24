package com.quorum.tessera.key.vault.hashicorp;

import static java.util.function.Predicate.not;

import com.quorum.tessera.key.vault.KeyVaultService;
import com.quorum.tessera.key.vault.SetSecretResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.VaultVersionedKeyValueOperations;
import org.springframework.vault.support.Versioned;

public class HashicorpKeyVaultService implements KeyVaultService {

  protected static final String SECRET_VERSION_KEY = "secretVersion";

  protected static final String SECRET_ID_KEY = "secretId";

  protected static final String SECRET_NAME_KEY = "secretName";

  protected static final String SECRET_ENGINE_NAME_KEY = "secretEngineName";

  protected static final String TRANSIT_SECRET_ENGINE_NAME_KEY = "transitSecretEngineName";

  protected static final String TRANSIT_KEY_NAME_KEY = "transitKeyName";

  private final VaultVersionedKeyValueTemplateFactory vaultVersionedKeyValueTemplateFactory;

  private final VaultOperations vaultOperations;

  private final HashicorpTransitSecretEngineService hashicorpTransitSecretEngineService;

  HashicorpKeyVaultService(
      VaultOperations vaultOperations,
      Supplier<VaultVersionedKeyValueTemplateFactory>
          vaultVersionedKeyValueTemplateFactorySupplier) {
    this.vaultOperations = vaultOperations;
    this.vaultVersionedKeyValueTemplateFactory =
        vaultVersionedKeyValueTemplateFactorySupplier.get();
    this.hashicorpTransitSecretEngineService =
        new HashicorpTransitSecretEngineService(this.vaultOperations);
  }

  @Override
  public String getSecret(Map<String, String> hashicorpGetSecretData) {

    final String secretName = hashicorpGetSecretData.get(SECRET_NAME_KEY);
    final String secretEngineName = hashicorpGetSecretData.get(SECRET_ENGINE_NAME_KEY);
    final int secretVersion =
        Optional.ofNullable(hashicorpGetSecretData.get(SECRET_VERSION_KEY))
            .map(Integer::parseInt)
            .orElse(0);
    final String secretId = hashicorpGetSecretData.get(SECRET_ID_KEY);

    VaultVersionedKeyValueOperations keyValueOperations =
        vaultVersionedKeyValueTemplateFactory.createVaultVersionedKeyValueTemplate(
            vaultOperations, secretEngineName);

    Versioned<Map<String, Object>> versionedResponse =
        keyValueOperations.get(secretName, Versioned.Version.from(secretVersion));

    if (versionedResponse == null || !versionedResponse.hasData()) {
      throw new HashicorpVaultException("No data found at " + secretEngineName + "/" + secretName);
    }

    if (!versionedResponse.getData().containsKey(secretId)) {
      throw new HashicorpVaultException(
          "No value with id " + secretId + " found at " + secretEngineName + "/" + secretName);
    }

    var secretValue = versionedResponse.getData().get(secretId).toString();

    return hashicorpTransitSecretEngineService.decryptValueIfTseRequired(
        hashicorpGetSecretData, secretValue);
  }

  private Map.Entry<String, String> encryptValueIfTseRequired(
      Map<String, String> hashicorpSetSecretData, Map.Entry<String, String> entry) {
    String newValue =
        hashicorpTransitSecretEngineService.encryptValueIfTseRequired(
            hashicorpSetSecretData, entry.getValue());
    return Map.entry(entry.getKey(), newValue);
  }

  @Override
  public SetSecretResponse setSecret(Map<String, String> hashicorpSetSecretData) {

    String secretName = hashicorpSetSecretData.get(SECRET_NAME_KEY);
    String secretEngineName = hashicorpSetSecretData.get(SECRET_ENGINE_NAME_KEY);

    final var propertiesToExcludeFromSavingToVaultSecret =
        List.of(
            SECRET_NAME_KEY,
            SECRET_ID_KEY,
            SECRET_ENGINE_NAME_KEY,
            TRANSIT_SECRET_ENGINE_NAME_KEY,
            TRANSIT_KEY_NAME_KEY);

    Map<String, String> nameValuePairs =
        hashicorpSetSecretData.entrySet().stream()
            .filter(not(e -> propertiesToExcludeFromSavingToVaultSecret.contains(e.getKey())))
            .map(e -> encryptValueIfTseRequired(hashicorpSetSecretData, e))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    VaultVersionedKeyValueOperations keyValueOperations =
        vaultVersionedKeyValueTemplateFactory.createVaultVersionedKeyValueTemplate(
            vaultOperations, secretEngineName);
    try {
      Versioned.Metadata metadata = keyValueOperations.put(secretName, nameValuePairs);
      return new SetSecretResponse(
          Map.of("version", String.valueOf(metadata.getVersion().getVersion())));
    } catch (NullPointerException ex) {
      throw new HashicorpVaultException(
          "Unable to save generated secret to vault.  Ensure that the secret engine being used is a v2 kv secret engine");
    }
  }
}
