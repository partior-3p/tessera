package com.quorum.tessera.key.vault;

import com.quorum.tessera.config.Config;
import com.quorum.tessera.config.KeyVaultType;
import com.quorum.tessera.config.util.EnvironmentVariableProvider;
import java.util.ServiceLoader;

public interface DbCredentialsVaultServiceFactory {
  DbCredentialsVaultService create(Config config, EnvironmentVariableProvider envProvider);

  KeyVaultType getType();

  static DbCredentialsVaultServiceFactory getInstance(KeyVaultType keyVaultType) {
    return ServiceLoader.load(DbCredentialsVaultServiceFactory.class).stream()
        .map(ServiceLoader.Provider::get)
        .filter(factory -> factory.getType() == keyVaultType)
        .findFirst()
        .orElseThrow(
            () ->
                new NoKeyVaultServiceFactoryException(
                    keyVaultType
                        + " implementation of DbCredentialVaultService was not found on the classpath"));
  }
}
