package com.quorum.tessera.key.vault.hashicorp;

import static com.quorum.tessera.config.util.EnvironmentVariables.*;

import com.quorum.tessera.config.Config;
import com.quorum.tessera.config.ConfigException;
import com.quorum.tessera.config.KeyVaultConfig;
import com.quorum.tessera.config.KeyVaultType;
import com.quorum.tessera.config.util.EnvironmentVariableProvider;
import com.quorum.tessera.key.vault.DbCredentialsVaultServiceFactory;
import java.util.List;
import java.util.Objects;

public class HashicorpDbCredentialsVaultServiceFactory extends HashicorpVaultServiceFactory
    implements DbCredentialsVaultServiceFactory {

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

    return super.create(
        config,
        envProvider,
        util,
        this::getKeyVaultConfig,
        HashicorpDbCredentialsVaultService::new,
        HASHICORP_DSE_ROLE_ID,
        HASHICORP_DSE_SECRET_ID,
        HASHICORP_DSE_TOKEN);
  }

  private KeyVaultConfig getKeyVaultConfig(Config config) {
    var keyVaultConfig =
        config.getJdbcConfig().getHashicorpVaultDbCredentialsConfig().toKeyVaultConfig();
    validateRequiredConfigurationPropertiesArePresent(keyVaultConfig);
    return keyVaultConfig;
  }

  void validateRequiredConfigurationPropertiesArePresent(KeyVaultConfig keyVaultConfig) {
    var requiredProperties = List.of("url", "dbSecretEngineName", "vaultDbRole", "approlePath");
    var missingProperties =
        requiredProperties.stream()
            .filter(
                propName ->
                    !keyVaultConfig.hasProperty(propName)
                        || keyVaultConfig.getProperty(propName).isEmpty())
            .toList();
    if (!missingProperties.isEmpty()) {
      throw new ConfigException(
          new HashicorpVaultException(
              String.format(
                  "[%s] missing in the configuration. This/these properties should be defined in configuration section: jdbc.hashicorpVaultDbCredentialsConfig",
                  String.join(", ", missingProperties))));
    }
  }

  public KeyVaultType getType() {
    return KeyVaultType.HASHICORP;
  }
}
