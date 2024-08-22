package com.quorum.tessera.key.vault.hashicorp;

import com.quorum.tessera.config.Config;
import com.quorum.tessera.config.KeyVaultType;
import com.quorum.tessera.config.util.EnvironmentVariableProvider;
import com.quorum.tessera.key.vault.DbCredentialsVaultServiceFactory;

import java.util.Objects;

public class HashicorpDbCredentialsVaultServiceFactory extends HashicorpVaultServiceFactory implements DbCredentialsVaultServiceFactory {

  public HashicorpDbCredentialsVaultService create(
      Config config, EnvironmentVariableProvider envProvider) {
    Objects.requireNonNull(config);
    Objects.requireNonNull(envProvider);

    HashicorpKeyVaultServiceFactoryUtil util = new HashicorpKeyVaultServiceFactoryUtil();

    return this.create(config, envProvider, util);
  }

  HashicorpDbCredentialsVaultService create(
      Config config,
      EnvironmentVariableProvider envProvider,
      HashicorpKeyVaultServiceFactoryUtil util) {

    return super.create(config, envProvider, util,
      (appConfig)->{
        return appConfig.getJdbcConfig()
          .getHashicorpVaultDbCredentialsConfig()
          .toKeyVaultConfig();
      },
      HashicorpDbCredentialsVaultService::new
    );
  }

  public KeyVaultType getType() {
    return KeyVaultType.HASHICORP;
  }
}
