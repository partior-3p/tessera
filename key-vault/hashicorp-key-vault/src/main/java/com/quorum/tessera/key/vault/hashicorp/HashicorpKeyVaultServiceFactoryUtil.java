package com.quorum.tessera.key.vault.hashicorp;

import static com.quorum.tessera.config.util.EnvironmentVariables.*;

import com.quorum.tessera.config.KeyVaultConfig;
import com.quorum.tessera.config.util.EnvironmentVariableProvider;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.vault.authentication.AppRoleAuthentication;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.ClientHttpRequestFactoryFactory;
import org.springframework.vault.client.RestTemplateBuilder;
import org.springframework.vault.client.SimpleVaultEndpointProvider;
import org.springframework.vault.client.VaultClients;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.web.client.RestOperations;

class HashicorpKeyVaultServiceFactoryUtil {

  public static final String NAMESPACE_KEY = "namespace";

  private static final Logger LOGGER =
      LoggerFactory.getLogger(HashicorpKeyVaultServiceFactoryUtil.class);

  SslConfiguration configureSsl(
      KeyVaultConfig keyVaultConfig, EnvironmentVariableProvider envProvider) {
    if (keyVaultConfig.hasProperty("tlsKeyStorePath", "tlsTrustStorePath")) {

      Path tlsKeyStorePath = keyVaultConfig.getProperty("tlsKeyStorePath").map(Paths::get).get();
      Path tlsTrustStorePath =
          keyVaultConfig.getProperty("tlsTrustStorePath").map(Paths::get).get();

      Resource clientKeyStore = new FileSystemResource(tlsKeyStorePath.toFile());
      Resource clientTrustStore = new FileSystemResource(tlsTrustStorePath.toFile());

      SslConfiguration.KeyStoreConfiguration keyStoreConfiguration =
          SslConfiguration.KeyStoreConfiguration.of(
              clientKeyStore, envProvider.getEnvAsCharArray(HASHICORP_CLIENT_KEYSTORE_PWD));

      SslConfiguration.KeyStoreConfiguration trustStoreConfiguration =
          SslConfiguration.KeyStoreConfiguration.of(
              clientTrustStore, envProvider.getEnvAsCharArray(HASHICORP_CLIENT_TRUSTSTORE_PWD));

      return new SslConfiguration(keyStoreConfiguration, trustStoreConfiguration);

    } else if (keyVaultConfig.hasProperty("tlsTrustStorePath")) {

      Path tlsTrustStorePath =
          keyVaultConfig.getProperty("tlsTrustStorePath").map(Paths::get).get();
      Resource clientTrustStore = new FileSystemResource(tlsTrustStorePath.toFile());

      return SslConfiguration.forTrustStore(
          clientTrustStore, envProvider.getEnvAsCharArray(HASHICORP_CLIENT_TRUSTSTORE_PWD));

    } else {
      return SslConfiguration.unconfigured();
    }
  }

  ClientHttpRequestFactory createClientHttpRequestFactory(
      ClientOptions clientOptions, SslConfiguration sslConfiguration) {
    return ClientHttpRequestFactoryFactory.create(clientOptions, sslConfiguration);
  }

  ClientAuthentication configureClientAuthentication(
      KeyVaultConfig keyVaultConfig,
      EnvironmentVariableProvider envProvider,
      ClientHttpRequestFactory clientHttpRequestFactory,
      VaultEndpoint vaultEndpoint) {
    return configureClientAuthentication(
        keyVaultConfig,
        envProvider,
        clientHttpRequestFactory,
        vaultEndpoint,
        HASHICORP_ROLE_ID,
        HASHICORP_SECRET_ID,
        HASHICORP_TOKEN);
  }

  ClientAuthentication configureClientAuthentication(
      KeyVaultConfig keyVaultConfig,
      EnvironmentVariableProvider envProvider,
      ClientHttpRequestFactory clientHttpRequestFactory,
      VaultEndpoint vaultEndpoint,
      String envVarHashicorpRoleId,
      String envVarHashicorpSecretId,
      String envVarHashicorpToken) {

    final String roleId = envProvider.getEnv(envVarHashicorpRoleId);
    final String secretId = envProvider.getEnv(envVarHashicorpSecretId);
    final String authToken = envProvider.getEnv(envVarHashicorpToken);

    if (roleId != null && secretId != null) {

      AppRoleAuthenticationOptions appRoleAuthenticationOptions =
          AppRoleAuthenticationOptions.builder()
              .path(keyVaultConfig.getProperty("approlePath").get())
              .roleId(AppRoleAuthenticationOptions.RoleId.provided(roleId))
              .secretId(AppRoleAuthenticationOptions.SecretId.provided(secretId))
              .build();

      RestOperations restOperations;
      if (keyVaultConfig.hasProperty(NAMESPACE_KEY)
          && keyVaultConfig.getProperty(NAMESPACE_KEY).isPresent()) {
        String namespace = keyVaultConfig.getProperty(NAMESPACE_KEY).get();
        LOGGER.info("Using namespace {} for login", namespace);
        var restTemplateBuilder =
            getRestTemplateWithVaultNamespace(namespace, clientHttpRequestFactory, vaultEndpoint);
        restOperations = restTemplateBuilder.build();
      } else {
        LOGGER.info("No namespace");
        restOperations = VaultClients.createRestTemplate(vaultEndpoint, clientHttpRequestFactory);
      }

      return new AppRoleAuthentication(appRoleAuthenticationOptions, restOperations);

    } else if (Objects.isNull(roleId) != Objects.isNull(secretId)) {

      throw new HashicorpCredentialNotSetException(
          "Both "
              + envVarHashicorpRoleId
              + " and "
              + envVarHashicorpSecretId
              + " environment variables must be set to use the AppRole authentication method");

    } else if (authToken == null) {

      throw new HashicorpCredentialNotSetException(
          "Both "
              + envVarHashicorpRoleId
              + " and "
              + envVarHashicorpSecretId
              + " environment variables must be set to use the AppRole authentication method.  Alternatively set "
              + envVarHashicorpToken
              + " to authenticate using the Token method");
    }

    return new TokenAuthentication(authToken);
  }

  RestTemplateBuilder getRestTemplateWithVaultNamespace(
      String namespace,
      ClientHttpRequestFactory clientHttpRequestFactory,
      VaultEndpoint vaultEndpoint) {
    return RestTemplateBuilder.builder()
        .endpointProvider(SimpleVaultEndpointProvider.of(vaultEndpoint))
        .requestFactory(clientHttpRequestFactory)
        .customizers(
            restTemplate ->
                restTemplate
                    .getInterceptors()
                    .add(VaultClients.createNamespaceInterceptor(namespace)));
  }
}
