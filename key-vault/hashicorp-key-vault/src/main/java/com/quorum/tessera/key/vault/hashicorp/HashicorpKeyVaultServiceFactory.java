package com.quorum.tessera.key.vault.hashicorp;

import com.quorum.tessera.config.*;
import com.quorum.tessera.config.util.EnvironmentVariableProvider;
import com.quorum.tessera.key.vault.KeyVaultService;
import com.quorum.tessera.key.vault.KeyVaultServiceFactory;
import java.util.Objects;
import java.util.Optional;
import org.springframework.vault.core.VaultOperations;

public class HashicorpKeyVaultServiceFactory extends HashicorpVaultServiceFactory
    implements KeyVaultServiceFactory {

  @Override
  public KeyVaultService create(Config config, EnvironmentVariableProvider envProvider) {
    Objects.requireNonNull(config);
    Objects.requireNonNull(envProvider);

    HashicorpKeyVaultServiceFactoryUtil util = new HashicorpKeyVaultServiceFactoryUtil();

    return this.create(config, envProvider, util);
  }

  // This method should not be called directly. It has been left package-private to enable injection
  // of util during
  // testing
  KeyVaultService create(
      Config config,
      EnvironmentVariableProvider envProvider,
      HashicorpKeyVaultServiceFactoryUtil util) {

    return super.create(
        config, envProvider, util, this::getKeyVaultConfig, this::getKeyVaultService);
  }

  private HashicorpKeyVaultService getKeyVaultService(
      VaultOperations vaultOperations, KeyVaultConfig keyVaultConfig) {
    return new HashicorpKeyVaultService(
        vaultOperations, () -> new VaultVersionedKeyValueTemplateFactory() {});
  }

  private KeyVaultConfig getKeyVaultConfig(Config config) {
    return Optional.ofNullable(config.getKeys())
        .flatMap(k -> k.getKeyVaultConfig(KeyVaultType.HASHICORP))
        .orElseThrow(
            () ->
                new ConfigException(
                    new RuntimeException(
                        "Trying to create Hashicorp Vault connection but no Vault configuration provided")));
  }

  @Override
  public KeyVaultType getType() {
    return KeyVaultType.HASHICORP;
  }
}
