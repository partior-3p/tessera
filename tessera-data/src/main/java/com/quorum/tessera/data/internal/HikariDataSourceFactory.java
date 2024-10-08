package com.quorum.tessera.data.internal;

import com.quorum.tessera.config.ConfigFactory;
import com.quorum.tessera.config.JdbcConfig;
import com.quorum.tessera.config.KeyVaultType;
import com.quorum.tessera.config.util.EncryptedStringResolver;
import com.quorum.tessera.config.util.EnvironmentVariableProvider;
import com.quorum.tessera.data.DataSourceFactory;
import com.quorum.tessera.key.vault.DbCredentials;
import com.quorum.tessera.key.vault.DbCredentialsVaultServiceFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;

public enum HikariDataSourceFactory implements DataSourceFactory {
  INSTANCE;

  private DataSource dataSource;

  @Override
  public DataSource create(JdbcConfig config) {
    if (dataSource != null) {
      return dataSource;
    }

    final EncryptedStringResolver resolver = new EncryptedStringResolver();

    final HikariConfig hikariConfig = new HikariConfig();
    hikariConfig.setJdbcUrl(config.getUrl());

    if (isDbCredentialsVaultConfigPresent(config)) {
      final var rootConfig = ConfigFactory.create().getConfig();
      final var dbCredentialsVaultService =
          DbCredentialsVaultServiceFactory.getInstance(KeyVaultType.HASHICORP)
              .create(rootConfig, new EnvironmentVariableProvider());
      final DbCredentials dbCredentials = dbCredentialsVaultService.getDbCredentials();

      hikariConfig.setUsername(dbCredentials.getUsername());
      hikariConfig.setPassword(dbCredentials.getPassword());
      var hikariDataSource = new HikariDataSource(hikariConfig);

      final var dbCredentialsVaultLifecycleManager =
          new DbCredentialsVaultLifecycleManager(
              dbCredentialsVaultService, config, hikariDataSource);
      dbCredentialsVaultLifecycleManager.start(dbCredentials.getLeaseDurationInSec());
      dataSource = hikariDataSource;

    } else {
      hikariConfig.setUsername(config.getUsername());
      hikariConfig.setPassword(resolver.resolve(config.getPassword()));
      dataSource = new HikariDataSource(hikariConfig);
    }

    return dataSource;
  }

  private boolean isDbCredentialsVaultConfigPresent(JdbcConfig config) {
    final var hashicorpVaultDbCredentialsConfig = config.getHashicorpVaultDbCredentialsConfig();
    return hashicorpVaultDbCredentialsConfig != null
        && hashicorpVaultDbCredentialsConfig.getKeyVaultType() == KeyVaultType.HASHICORP;
  }

  protected void clear() {
    dataSource = null;
  }
}
