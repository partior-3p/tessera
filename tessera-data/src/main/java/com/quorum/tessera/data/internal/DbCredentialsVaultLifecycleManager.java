package com.quorum.tessera.data.internal;

import com.quorum.tessera.config.JdbcConfig;
import com.quorum.tessera.key.vault.DbCredentialsVaultService;
import com.zaxxer.hikari.HikariDataSource;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DbCredentialsVaultLifecycleManager {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DbCredentialsVaultLifecycleManager.class);

  private final DbCredentialsVaultService dbCredentialsVaultService;
  private final JdbcConfig jdbcConfig;

  private final HikariDataSource hikariDataSource;

  private final ScheduledExecutorService scheduledExecutorService;

  private final long retryDelayInSeconds = 2;
  private int maxRetryDelayInSeconds = 60;
  private int retryCount = 0;

  private final long minDelayBeforeNextRunFactor = 10;
  private final double delayBeforeNextRunFactor = 0.1;
  private final long maxDurationBeforeTtlExpireInSeconds = 300;

  private long getDelayBeforeNextRunInSecondsBasedOnTtl(final long ttlInSeconds) {
    var durationBeforeTtlExpire = (long) Math.ceil(ttlInSeconds * delayBeforeNextRunFactor);
    durationBeforeTtlExpire =
        Math.min(durationBeforeTtlExpire, maxDurationBeforeTtlExpireInSeconds);
    var delayBeforeNextRunInSeconds = ttlInSeconds - durationBeforeTtlExpire;
    return Math.max(delayBeforeNextRunInSeconds, minDelayBeforeNextRunFactor);
  }

  private long getRetryDelayInSeconds() {
    var factor = 2;
    var maxRetryCount =
        (int) (Math.log((double) maxRetryDelayInSeconds / retryDelayInSeconds) / Math.log(factor));

    if (retryCount > maxRetryCount) {
      retryCount = maxRetryCount;
      return maxRetryDelayInSeconds;
    }

    return retryDelayInSeconds * (long) Math.pow(factor, retryCount);
  }

  private void checkAndRetrieveNewDbCredentials() {
    try {
      LOGGER.info("Checking for new db credentials from vault ...");

      final var credentials = dbCredentialsVaultService.getDbCredentials();
      final long adjustedDelayBeforeNextRunInSeconds =
          getDelayBeforeNextRunInSecondsBasedOnTtl(credentials.getLeaseDurationInSec());

      final var hikariConfigMXBean = hikariDataSource.getHikariConfigMXBean();
      hikariConfigMXBean.setUsername(credentials.getUsername());
      hikariConfigMXBean.setPassword(credentials.getPassword());

      final var hikariPoolMXBean = hikariDataSource.getHikariPoolMXBean();
      hikariPoolMXBean.softEvictConnections();

      scheduledExecutorService.schedule(
          this::checkAndRetrieveNewDbCredentials,
          adjustedDelayBeforeNextRunInSeconds,
          TimeUnit.SECONDS);
      retryCount = 0;
      LOGGER.info(
          "Checking for new db credentials from vault was successful. checking again after {} seconds",
          adjustedDelayBeforeNextRunInSeconds);
    } catch (Exception ex) {
      var delayInSeconds = getRetryDelayInSeconds();
      LOGGER.error(
          "Unexpected error while fetching new db credentials. Retrying after {} seconds. Error: {}",
          delayInSeconds,
          getNestedExceptionMessagesSummary(ex));
      LOGGER.debug("Error details", ex);
      scheduledExecutorService.schedule(
          this::checkAndRetrieveNewDbCredentials, delayInSeconds, TimeUnit.SECONDS);
      retryCount++;
    }
  }

  public DbCredentialsVaultLifecycleManager(
      DbCredentialsVaultService dbCredentialsVaultService,
      JdbcConfig jdbcConfig,
      HikariDataSource hikariDataSource) {
    this.dbCredentialsVaultService = dbCredentialsVaultService;
    this.jdbcConfig = jdbcConfig;
    this.hikariDataSource = hikariDataSource;
    this.scheduledExecutorService = Executors.newScheduledThreadPool(2);
  }

  String getNestedExceptionMessagesSummary(Throwable e) {
    StringBuilder sb = new StringBuilder();
    int levelCount = 0;
    final int maxLevelCount = 5;
    while (e != null) {
      if (levelCount > 0) {
        sb.append(". ");
      }
      sb.append(e.getMessage());
      e = e.getCause();
      levelCount++;
      if (levelCount > maxLevelCount) {
        sb.append("...");
        break;
      }
    }
    return sb.toString();
  }

  public void start(final long initialDelayInSeconds) {
    LOGGER.info("Component for managing life-cycle of db credentials in vault is starting ...");
    var adjustedDelayInSeconds = getDelayBeforeNextRunInSecondsBasedOnTtl(initialDelayInSeconds);
    scheduledExecutorService.schedule(
        this::checkAndRetrieveNewDbCredentials, adjustedDelayInSeconds, TimeUnit.SECONDS);
    LOGGER.info(
        "Component for managing life-cycle of db credentials in vault is successfully started. Next schedule is after {} seconds",
        adjustedDelayInSeconds);
  }

  public void stop() {
    scheduledExecutorService.shutdown();
  }
}
