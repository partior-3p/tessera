package com.quorum.tessera.key.vault.hashicorp;

import com.quorum.tessera.config.Config;
import com.quorum.tessera.config.KeyVaultConfig;
import com.quorum.tessera.config.KeyVaultType;
import com.quorum.tessera.config.util.EnvironmentVariableProvider;
import com.quorum.tessera.key.vault.DbCredentialsVaultServiceFactory;

import java.util.List;
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
        var keyVaultConfig = appConfig.getJdbcConfig()
          .getHashicorpVaultDbCredentialsConfig()
          .toKeyVaultConfig();
        validateRequiredConfigurationPropertiesArePresent(keyVaultConfig);
        return keyVaultConfig;
      },
      HashicorpDbCredentialsVaultService::new
    );
  }

  void validateRequiredConfigurationPropertiesArePresent(KeyVaultConfig keyVaultConfig){
    var requiredProperties = List.of("url","dbSecretEngineName","vaultDbRole","approlePath");
    var missingProperties = requiredProperties.stream().filter(
      propName->
        !keyVaultConfig.hasProperty(propName) || keyVaultConfig.getProperty(propName).isEmpty()
    ).toList();
    if (!missingProperties.isEmpty()){
      throw new HashicorpVaultException(
        String.format("[%s] missing in the configuration. This/these properties should be defined in configuration section: jdbc.hashicorpVaultDbCredentialsConfig", String.join(", ", missingProperties))
      );
    }
  }

  public KeyVaultType getType() {
    return KeyVaultType.HASHICORP;
  }
}
