package com.quorum.tessera.data.internal;

import com.quorum.tessera.config.ConfigException;
import com.quorum.tessera.config.JdbcConfig;
import com.quorum.tessera.key.vault.DbCredentialsVaultService;
import com.zaxxer.hikari.HikariDataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DbCredentialsVaultLifecycleManager {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DbCredentialsVaultLifecycleManager.class);

  private final DbCredentialsVaultService dbCredentialsVaultService;
  private final JdbcConfig jdbcConfig;

  private final HikariDataSource hikariDataSource;

  private final ScheduledExecutorService scheduledExecutorService;

  private long retryDelayInSeconds = 2;
  private int maxRetryDelayInSeconds = 60;
  private int retryCount = 0;
  private long minDelayBeforeNextRunInSeconds = 10;
  private double delayBeforeNextRunFactor = 0.1;
  private long maxDurationBeforeTtlExpireInSeconds = 300;

  private long getDelayBeforeNextRunInSecondsBasedOnTtl(final long ttlInSeconds) {
    var durationBeforeTtlExpire = (long) Math.ceil(ttlInSeconds * delayBeforeNextRunFactor);
    durationBeforeTtlExpire =
        Math.min(durationBeforeTtlExpire, maxDurationBeforeTtlExpireInSeconds);
    var delayBeforeNextRunInSeconds = ttlInSeconds - durationBeforeTtlExpire;
    return Math.max(delayBeforeNextRunInSeconds, minDelayBeforeNextRunInSeconds);
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
    getAndAssignConfigurations();
  }

  String getNestedExceptionMessagesSummary(Throwable e) {
    StringBuilder sb = new StringBuilder();
    int levelCount = 0;
    final int maxLevelCount = 5;
    var exception = e;
    while (exception != null) {
      if (levelCount > 0) {
        sb.append(". ");
      }
      sb.append(exception.getMessage());
      exception = exception.getCause();
      levelCount++;
      if (levelCount > maxLevelCount) {
        sb.append("...");
        break;
      }
    }
    return sb.toString();
  }

  private void getAndAssignConfigurations() {
    var vaultDbCredentialsConfig = jdbcConfig.getHashicorpVaultDbCredentialsConfig();
    var errors = new ArrayList<String>();
    this.retryDelayInSeconds =
        validateAndConvertValue(
            "retryDelayInSeconds",
            vaultDbCredentialsConfig.getRetryDelayInSeconds(),
            Long::parseLong,
            List.of(this::validateValueShouldBeGreaterThanZero),
            2L,
            errors);
    this.maxRetryDelayInSeconds =
        validateAndConvertValue(
            "maxRetryDelayInSeconds",
            vaultDbCredentialsConfig.getMaxRetryDelayInSeconds(),
            Integer::parseInt,
            List.of(this::validateValueShouldBeGreaterThanZero),
            60,
            errors);
    this.minDelayBeforeNextRunInSeconds =
        validateAndConvertValue(
            "minDelayBeforeNextRunInSeconds",
            vaultDbCredentialsConfig.getMinDelayBeforeNextRunInSeconds(),
            Long::parseLong,
            List.of(this::validateValueShouldBeGreaterThanZero),
            10L,
            errors);
    this.delayBeforeNextRunFactor =
        validateAndConvertValue(
            "delayBeforeNextRunFactor",
            vaultDbCredentialsConfig.getDelayBeforeNextRunFactor(),
            Double::parseDouble,
            List.of(this::validateValueShouldBeGreaterThanZero),
            0.1,
            errors);
    this.maxDurationBeforeTtlExpireInSeconds =
        validateAndConvertValue(
            "maxDurationBeforeTtlExpireInSeconds",
            vaultDbCredentialsConfig.getMaxDurationBeforeTtlExpireInSeconds(),
            Long::parseLong,
            List.of(this::validateValueShouldBeGreaterThanZero),
            300L,
            errors);
    if (!errors.isEmpty()) {
      throw new ConfigException(String.join(" ", errors));
    }
  }

  private <R> R validateAndConvertValue(
      final String propertyName,
      final String value,
      Function<String, R> converter,
      List<TriConsumer<String, R, List<String>>> validators,
      final R defaultValue,
      List<String> outputError) {
    if (value == null) {
      return defaultValue;
    }
    try {
      var convertedValue = converter.apply(value);
      for (var validator : validators) {
        validator.accept(propertyName, convertedValue, outputError);
      }
      return convertedValue;
    } catch (Exception ex) {
      outputError.add(
          String.format(
              "The value \"%s\" of property [%s] is not a valid [%s] type.",
              value, propertyName, defaultValue.getClass().getSimpleName()));
    }
    return defaultValue;
  }

  private void validateValueShouldBeGreaterThanZero(
      String propertyName, Number value, List<String> outputError) {
    if ((value instanceof Double && value.doubleValue() <= 0)
        || (value instanceof Integer && value.intValue() <= 0)
        || (value instanceof Long && value.longValue() <= 0)) {
      outputError.add(
          String.format(
              "Configuration property \"%s\" should have a value greater than zero (0).",
              propertyName));
    }
  }

  private void logStartupConfiguration() {
    StringBuilder sb = new StringBuilder();
    sb.append("retryDelayInSeconds=").append(this.retryDelayInSeconds).append("; ");
    sb.append("maxRetryDelayInSeconds=").append(this.maxRetryDelayInSeconds).append("; ");
    sb.append("minDelayBeforeNextRunInSeconds=")
        .append(this.minDelayBeforeNextRunInSeconds)
        .append("; ");
    sb.append("delayBeforeNextRunFactor=").append(this.delayBeforeNextRunFactor).append("; ");
    sb.append("maxDurationBeforeTtlExpireInSeconds=")
        .append(this.maxDurationBeforeTtlExpireInSeconds)
        .append("; ");
    LOGGER.info(
        "Component for managing life-cycle of db credentials in vault started with configurations: {}",
        sb.toString());
  }

  public void start(final long initialDelayInSeconds) {
    LOGGER.info("Component for managing life-cycle of db credentials in vault is starting ...");
    var adjustedDelayInSeconds = getDelayBeforeNextRunInSecondsBasedOnTtl(initialDelayInSeconds);
    scheduledExecutorService.schedule(
        this::checkAndRetrieveNewDbCredentials, adjustedDelayInSeconds, TimeUnit.SECONDS);
    LOGGER.info(
        "Component for managing life-cycle of db credentials in vault is successfully started. Next schedule is after {} seconds",
        adjustedDelayInSeconds);
    logStartupConfiguration();
  }

  public void stop() {
    scheduledExecutorService.shutdown();
  }

  @FunctionalInterface
  public interface TriConsumer<T, U, V> {
    void accept(T t, U u, V v);
  }
}
