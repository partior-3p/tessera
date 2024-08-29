package com.quorum.tessera.key.vault.hashicorp;

import static com.quorum.tessera.config.util.EnvironmentVariables.*;
import static com.quorum.tessera.key.vault.hashicorp.HashicorpKeyVaultServiceFactoryUtil.NAMESPACE_KEY;

import com.quorum.tessera.config.Config;
import com.quorum.tessera.config.ConfigException;
import com.quorum.tessera.config.KeyVaultConfig;
import com.quorum.tessera.config.util.EnvironmentVariableProvider;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.SessionManager;
import org.springframework.vault.client.RestTemplateBuilder;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.SslConfiguration;

class HashicorpVaultServiceFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(HashicorpVaultServiceFactory.class);

  <R> R create(
      Config config,
      EnvironmentVariableProvider envProvider,
      HashicorpKeyVaultServiceFactoryUtil util,
      Function<Config, KeyVaultConfig> keyVaultConfigProvider,
      BiFunction<VaultOperations, KeyVaultConfig, R> keyVaultServiceProvider) {
    return create(
        config,
        envProvider,
        util,
        keyVaultConfigProvider,
        keyVaultServiceProvider,
        HASHICORP_ROLE_ID,
        HASHICORP_SECRET_ID,
        HASHICORP_TOKEN);
  }

  <R> R create(
      Config config,
      EnvironmentVariableProvider envProvider,
      HashicorpKeyVaultServiceFactoryUtil util,
      Function<Config, KeyVaultConfig> keyVaultConfigProvider,
      BiFunction<VaultOperations, KeyVaultConfig, R> keyVaultServiceProvider,
      String envVarHashicorpRoleId,
      String envVarHashicorpSecretId,
      String envVarHashicorpToken) {

    Objects.requireNonNull(config);
    Objects.requireNonNull(envProvider);
    Objects.requireNonNull(util);
    Objects.requireNonNull(keyVaultConfigProvider);
    Objects.requireNonNull(keyVaultServiceProvider);

    final String roleId = envProvider.getEnv(envVarHashicorpRoleId);
    final String secretId = envProvider.getEnv(envVarHashicorpSecretId);
    final String authToken = envProvider.getEnv(envVarHashicorpToken);

    if (roleId == null && secretId == null && authToken == null) {
      throw new HashicorpCredentialNotSetException(
          "Environment variables must be set to authenticate with Hashicorp Vault.  Set the "
              + envVarHashicorpRoleId
              + " and "
              + envVarHashicorpSecretId
              + " environment variables if using the AppRole authentication method.  Set the "
              + envVarHashicorpToken
              + " environment variable if using another authentication method.");
    } else if (isOnlyOneInputNull(roleId, secretId)) {
      throw new HashicorpCredentialNotSetException(
          "Only one of the "
              + envVarHashicorpRoleId
              + " and "
              + envVarHashicorpSecretId
              + " environment variables to authenticate with Hashicorp Vault using the AppRole method has been set");
    }

    KeyVaultConfig keyVaultConfig = keyVaultConfigProvider.apply(config);

    VaultEndpoint vaultEndpoint;

    try {
      URI uri = new URI(keyVaultConfig.getProperty("url").get());
      LOGGER.info("URL for Hashicorp key vault is {}", keyVaultConfig.getProperty("url").get());
      vaultEndpoint = VaultEndpoint.from(uri);
    } catch (URISyntaxException | NoSuchElementException | IllegalArgumentException e) {
      throw new ConfigException(
          new HashicorpVaultException("Provided Hashicorp Vault url is incorrectly formatted"));
    }

    SslConfiguration sslConfiguration = util.configureSsl(keyVaultConfig, envProvider);

    ClientOptions clientOptions = new ClientOptions();

    ClientHttpRequestFactory clientHttpRequestFactory =
        util.createClientHttpRequestFactory(clientOptions, sslConfiguration);

    ClientAuthentication clientAuthentication =
        util.configureClientAuthentication(
            keyVaultConfig,
            envProvider,
            clientHttpRequestFactory,
            vaultEndpoint,
            envVarHashicorpRoleId,
            envVarHashicorpSecretId,
            envVarHashicorpToken);

    SessionManager sessionManager = new HashicorpSimpleSessionManager(clientAuthentication);

    VaultOperations vaultOperations =
        getVaultOperations(
            keyVaultConfig, vaultEndpoint, clientHttpRequestFactory, sessionManager, util);

    return keyVaultServiceProvider.apply(vaultOperations, keyVaultConfig);
  }

  private VaultOperations getVaultOperations(
      KeyVaultConfig keyVaultConfig,
      VaultEndpoint vaultEndpoint,
      ClientHttpRequestFactory clientHttpRequestFactory,
      SessionManager sessionManager,
      HashicorpKeyVaultServiceFactoryUtil util) {

    VaultOperations vaultOperations;

    if (keyVaultConfig.hasProperty(NAMESPACE_KEY)
        && keyVaultConfig.getProperty(NAMESPACE_KEY).isPresent()) {
      LOGGER.info(
          "Namespace for Hashicorp key vault is {}",
          keyVaultConfig.getProperty(NAMESPACE_KEY).get());

      String namespace = keyVaultConfig.getProperty(NAMESPACE_KEY).get();
      RestTemplateBuilder restTemplateBuilder =
          util.getRestTemplateWithVaultNamespace(
              namespace, clientHttpRequestFactory, vaultEndpoint);

      vaultOperations = new VaultTemplate(restTemplateBuilder, sessionManager);
    } else {
      vaultOperations = new VaultTemplate(vaultEndpoint, clientHttpRequestFactory, sessionManager);
    }

    return vaultOperations;
  }

  private boolean isOnlyOneInputNull(Object obj1, Object obj2) {
    return Objects.isNull(obj1) ^ Objects.isNull(obj2);
  }
}
