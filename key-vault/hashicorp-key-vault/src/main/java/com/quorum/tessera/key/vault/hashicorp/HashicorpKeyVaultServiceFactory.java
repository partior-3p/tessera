package com.quorum.tessera.key.vault.hashicorp;

import static com.quorum.tessera.config.util.EnvironmentVariables.*;

import com.quorum.tessera.config.*;
import com.quorum.tessera.config.util.EnvironmentVariableProvider;
import com.quorum.tessera.key.vault.KeyVaultService;
import com.quorum.tessera.key.vault.KeyVaultServiceFactory;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.SessionManager;
import org.springframework.vault.authentication.SimpleSessionManager;
import org.springframework.vault.client.RestTemplateBuilder;
import org.springframework.vault.client.SimpleVaultEndpointProvider;
import org.springframework.vault.client.VaultClients;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.SslConfiguration;

public class HashicorpKeyVaultServiceFactory implements KeyVaultServiceFactory {

  protected static final String NAMESPACE_KEY = "namespace";

  private static final Logger LOGGER = LoggerFactory.getLogger(HashicorpKeyVaultServiceFactory.class);


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
    Objects.requireNonNull(config);
    Objects.requireNonNull(envProvider);
    Objects.requireNonNull(util);

    final String roleId = envProvider.getEnv(HASHICORP_ROLE_ID);
    final String secretId = envProvider.getEnv(HASHICORP_SECRET_ID);
    final String authToken = envProvider.getEnv(HASHICORP_TOKEN);

    if (roleId == null && secretId == null && authToken == null) {
      throw new HashicorpCredentialNotSetException(
        "Environment variables must be set to authenticate with Hashicorp Vault.  Set the "
          + HASHICORP_ROLE_ID
          + " and "
          + HASHICORP_SECRET_ID
          + " environment variables if using the AppRole authentication method.  Set the "
          + HASHICORP_TOKEN
          + " environment variable if using another authentication method.");
    } else if (isOnlyOneInputNull(roleId, secretId)) {
      throw new HashicorpCredentialNotSetException(
        "Only one of the "
          + HASHICORP_ROLE_ID
          + " and "
          + HASHICORP_SECRET_ID
          + " environment variables to authenticate with Hashicorp Vault using the AppRole method has been set");
    }

    KeyVaultConfig keyVaultConfig =
      Optional.ofNullable(config.getKeys())
        .flatMap(k -> k.getKeyVaultConfig(KeyVaultType.HASHICORP))
        .orElseThrow(
          () ->
            new ConfigException(
              new RuntimeException(
                "Trying to create Hashicorp Vault connection but no Vault configuration provided")));

    VaultEndpoint vaultEndpoint;

    try {
      URI uri = new URI(keyVaultConfig.getProperty("url").get());
      LOGGER.info("URL for Hashicorp key vault is {}", keyVaultConfig.getProperty("url").get());
      vaultEndpoint = VaultEndpoint.from(uri);
    } catch (URISyntaxException | NoSuchElementException | IllegalArgumentException e) {
      throw new ConfigException(
        new RuntimeException("Provided Hashicorp Vault url is incorrectly formatted", e));
    }
    LOGGER.info("Namespace for Hashicorp key vault is {}", keyVaultConfig.getProperty(NAMESPACE_KEY).get());

    SslConfiguration sslConfiguration = util.configureSsl(keyVaultConfig, envProvider);

    ClientOptions clientOptions = new ClientOptions();

    ClientHttpRequestFactory clientHttpRequestFactory =
      util.createClientHttpRequestFactory(clientOptions, sslConfiguration);

    LOGGER.info("Before client authentication to the vault");

    ClientAuthentication clientAuthentication =
      util.configureClientAuthentication(
        keyVaultConfig, envProvider, clientHttpRequestFactory, vaultEndpoint);

    LOGGER.info("Before Creating simple session manager");

    SessionManager sessionManager = new SimpleSessionManager(clientAuthentication);

    LOGGER.info("After Creating simple session manager");

    VaultOperations vaultOperations =
      getVaultOperations(keyVaultConfig, vaultEndpoint, clientHttpRequestFactory, sessionManager);

    LOGGER.info("Fetched Vault operations");
    return new HashicorpKeyVaultService(
      vaultOperations, () -> new VaultVersionedKeyValueTemplateFactory() {});
  }

  private VaultOperations getVaultOperations(
    KeyVaultConfig keyVaultConfig,
    VaultEndpoint vaultEndpoint,
    ClientHttpRequestFactory clientHttpRequestFactory,
    SessionManager sessionManager) {

    VaultOperations vaultOperations;
    LOGGER.info("Before Entering getVaultOperations IF Loop");

    if (keyVaultConfig.hasProperty(NAMESPACE_KEY)
      && keyVaultConfig.getProperty(NAMESPACE_KEY).isPresent()) {
      LOGGER.info("Inside getVaultOperations IF Loop");

      String namespace = keyVaultConfig.getProperty(NAMESPACE_KEY).get();
      RestTemplateBuilder restTemplateBuilder =
        RestTemplateBuilder.builder()
          .endpointProvider(SimpleVaultEndpointProvider.of(vaultEndpoint))
          .requestFactory(clientHttpRequestFactory)
          .customizers(
            restTemplate ->
              restTemplate
                .getInterceptors()
                .add(VaultClients.createNamespaceInterceptor(namespace)));
      LOGGER.info("RestTemplateBuilder Built");
      vaultOperations = new VaultTemplate(restTemplateBuilder, sessionManager);
    } else {
      LOGGER.info("Inside getVaultOperations Else Loop");

      vaultOperations = new VaultTemplate(vaultEndpoint, clientHttpRequestFactory, sessionManager);
    }

    return vaultOperations;
  }

  @Override
  public KeyVaultType getType() {
    return KeyVaultType.HASHICORP;
  }

  private boolean isOnlyOneInputNull(Object obj1, Object obj2) {
    return Objects.isNull(obj1) ^ Objects.isNull(obj2);
  }
}
