package com.quorum.tessera.key.vault.hashicorp;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.VaultTransitTemplate;

class HashicorpTransitSecretEngineService {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(HashicorpTransitSecretEngineService.class);
  private final VaultOperations vaultOperations;

  HashicorpTransitSecretEngineService(VaultOperations vaultOperations) {
    this.vaultOperations = vaultOperations;
  }

  private VaultTransitTemplate createVaultTransitTemplate(String transitSecretEngineName) {
    return new VaultTransitTemplate(vaultOperations, transitSecretEngineName);
  }

  private boolean validateIfValueIsTobeDecryptedByTse(
      String transitSecretEngineName, String transitKeyName, String value) {
    if (value != null && value.startsWith("vault:v")) {
      if (transitSecretEngineName == null || transitSecretEngineName.isEmpty()) {
        throw new HashicorpVaultException(
            "Value needs to be decrypted, but \""
                + HashicorpKeyVaultService.TRANSIT_SECRET_ENGINE_NAME_KEY
                + "\" property is not provided");
      }
      if (transitKeyName == null || transitKeyName.isEmpty()) {
        throw new HashicorpVaultException(
            "Value needs to be decrypted, but \""
                + HashicorpKeyVaultService.TRANSIT_KEY_NAME_KEY
                + "\" property is not provided");
      }
      return true;
    }
    return false;
  }

  private boolean validateIfValueNeedsToBeEncryptedByTse(
      String transitSecretEngineName, String transitKeyName, String value) {
    if (transitSecretEngineName != null
        && !transitSecretEngineName.isEmpty()
        && transitKeyName != null
        && !transitKeyName.isEmpty()
        && value != null
        && !value.isEmpty()) {
      return true;
    }
    return false;
  }

  String decryptValueIfTseRequired(Map<String, String> hashicorpGetSecretData, String value) {
    String transitSecretEngineName =
        hashicorpGetSecretData.get(HashicorpKeyVaultService.TRANSIT_SECRET_ENGINE_NAME_KEY);
    String transitKeyName =
        hashicorpGetSecretData.get(HashicorpKeyVaultService.TRANSIT_KEY_NAME_KEY);
    if (validateIfValueIsTobeDecryptedByTse(transitSecretEngineName, transitKeyName, value)) {
      var vaultTransitTemplate = createVaultTransitTemplate(transitSecretEngineName);
      var decryptedValue = vaultTransitTemplate.decrypt(transitKeyName, value);
      LOGGER.info("Value decrypted successfully using TSE");
      return decryptedValue;
    }
    return value;
  }

  String encryptValueIfTseRequired(Map<String, String> hashicorpGetSecretData, String value) {
    String transitSecretEngineName =
        hashicorpGetSecretData.get(HashicorpKeyVaultService.TRANSIT_SECRET_ENGINE_NAME_KEY);
    String transitKeyName =
        hashicorpGetSecretData.get(HashicorpKeyVaultService.TRANSIT_KEY_NAME_KEY);
    if (validateIfValueNeedsToBeEncryptedByTse(transitSecretEngineName, transitKeyName, value)) {
      var vaultTransitTemplate = createVaultTransitTemplate(transitSecretEngineName);
      var encryptedValue = vaultTransitTemplate.encrypt(transitKeyName, value);
      LOGGER.info("Value encrypted successfully using TSE");
      return encryptedValue;
    }
    return value;
  }
}
