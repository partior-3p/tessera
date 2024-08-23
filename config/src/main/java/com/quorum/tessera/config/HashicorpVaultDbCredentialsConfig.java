package com.quorum.tessera.config;

import com.quorum.tessera.config.adapters.PathAdapter;
import com.quorum.tessera.config.constraints.ValidPath;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

@XmlAccessorType(XmlAccessType.FIELD)
public class HashicorpVaultDbCredentialsConfig extends ConfigItem {

  @Valid @NotNull @XmlElement private String url;

  @Valid @XmlElement private String namespace;

  @Valid @XmlElement private String approlePath;

  @Valid @NotNull @XmlElement private String dbSecretEngineName;

  @Valid @NotNull @XmlElement private String vaultDbRole;

  @Valid @XmlElement private String credentialType;

  @Valid
  @ValidPath(checkExists = true, message = "File does not exist")
  @XmlElement(type = String.class)
  @XmlJavaTypeAdapter(PathAdapter.class)
  private Path tlsKeyStorePath;

  @Valid
  @ValidPath(checkExists = true, message = "File does not exist")
  @XmlElement(type = String.class)
  @XmlJavaTypeAdapter(PathAdapter.class)
  private Path tlsTrustStorePath;

  @Valid @XmlElement private String retryDelayInSeconds;
  @Valid @XmlElement private String maxRetryDelayInSeconds;
  @Valid @XmlElement private String minDelayBeforeNextRunInSeconds;
  @Valid @XmlElement private String delayBeforeNextRunFactor;
  @Valid @XmlElement private String maxDurationBeforeTtlExpireInSeconds;

  public static HashicorpVaultDbCredentialsConfig clone(
      HashicorpVaultDbCredentialsConfig instance) {
    if (instance == null) {
      return null;
    }
    var newInstance = new HashicorpVaultDbCredentialsConfig();
    newInstance.url = instance.url;
    newInstance.namespace = instance.namespace;
    newInstance.approlePath = instance.approlePath;
    newInstance.dbSecretEngineName = instance.dbSecretEngineName;
    newInstance.vaultDbRole = instance.vaultDbRole;
    newInstance.credentialType = instance.credentialType;
    newInstance.tlsKeyStorePath = instance.tlsKeyStorePath;
    newInstance.tlsTrustStorePath = instance.tlsTrustStorePath;
    newInstance.retryDelayInSeconds = instance.retryDelayInSeconds;
    newInstance.maxRetryDelayInSeconds = instance.maxRetryDelayInSeconds;
    newInstance.minDelayBeforeNextRunInSeconds = instance.minDelayBeforeNextRunInSeconds;
    newInstance.delayBeforeNextRunFactor = instance.delayBeforeNextRunFactor;
    newInstance.maxDurationBeforeTtlExpireInSeconds = instance.maxDurationBeforeTtlExpireInSeconds;
    return newInstance;
  }

  public String getRetryDelayInSeconds() {
    return retryDelayInSeconds;
  }

  void setRetryDelayInSeconds(String retryDelayInSeconds) {
    this.retryDelayInSeconds = retryDelayInSeconds;
  }

  public String getMaxRetryDelayInSeconds() {
    return maxRetryDelayInSeconds;
  }

  void setMaxRetryDelayInSeconds(String maxRetryDelayInSeconds) {
    this.maxRetryDelayInSeconds = maxRetryDelayInSeconds;
  }

  public String getMinDelayBeforeNextRunInSeconds() {
    return minDelayBeforeNextRunInSeconds;
  }

  void setMinDelayBeforeNextRunInSeconds(String minDelayBeforeNextRunInSeconds) {
    this.minDelayBeforeNextRunInSeconds = minDelayBeforeNextRunInSeconds;
  }

  public String getDelayBeforeNextRunFactor() {
    return delayBeforeNextRunFactor;
  }

  void setDelayBeforeNextRunFactor(String delayBeforeNextRunFactor) {
    this.delayBeforeNextRunFactor = delayBeforeNextRunFactor;
  }

  public String getMaxDurationBeforeTtlExpireInSeconds() {
    return maxDurationBeforeTtlExpireInSeconds;
  }

  void setMaxDurationBeforeTtlExpireInSeconds(String maxDurationBeforeTtlExpireInSeconds) {
    this.maxDurationBeforeTtlExpireInSeconds = maxDurationBeforeTtlExpireInSeconds;
  }

  public HashicorpVaultDbCredentialsConfig() {}

  public String getUrl() {
    return this.url;
  }

  void setUrl(String url) {
    this.url = url;
  }

  public String getNamespace() {
    return namespace;
  }

  void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  public String getDbSecretEngineName() {
    if (dbSecretEngineName == null) {
      return "database";
    }
    return dbSecretEngineName;
  }

  void setDbSecretEngineName(String dbSecretEngineName) {
    this.dbSecretEngineName = dbSecretEngineName;
  }

  public String getVaultDbRole() {
    return vaultDbRole;
  }

  void setVaultDbRole(String vaultDbRole) {
    this.vaultDbRole = vaultDbRole;
  }

  public Path getTlsKeyStorePath() {
    return tlsKeyStorePath;
  }

  void setTlsKeyStorePath(Path tlsKeyStorePath) {
    this.tlsKeyStorePath = tlsKeyStorePath;
  }

  public Path getTlsTrustStorePath() {
    return tlsTrustStorePath;
  }

  void setTlsTrustStorePath(Path tlsTrustStorePath) {
    this.tlsTrustStorePath = tlsTrustStorePath;
  }

  public String getApprolePath() {
    if (approlePath == null) {
      return "approle";
    }
    return approlePath;
  }

  void setApprolePath(String approlePath) {
    this.approlePath = approlePath;
  }

  public KeyVaultType getKeyVaultType() {
    return KeyVaultType.HASHICORP;
  }

  public String getCredentialType() {
    return credentialType;
  }

  void setCredentialType(String credentialType) {
    this.credentialType = credentialType;
  }

  public DefaultKeyVaultConfig toKeyVaultConfig() {
    DefaultKeyVaultConfig config = new DefaultKeyVaultConfig();
    config.setKeyVaultType(this.getKeyVaultType());
    config.setProperty("url", this.getUrl());
    config.setProperty("approlePath", this.getApprolePath());
    config.setProperty("dbSecretEngineName", this.getDbSecretEngineName());
    config.setProperty("vaultDbRole", this.getVaultDbRole());
    config.setProperty("credentialType", this.getCredentialType());
    config.setProperty("namespace", this.getNamespace());
    config.setProperty("retryDelayInSeconds", this.getRetryDelayInSeconds());
    config.setProperty("maxRetryDelayInSeconds", this.getMaxRetryDelayInSeconds());
    config.setProperty("minDelayBeforeNextRunInSeconds", this.getMinDelayBeforeNextRunInSeconds());
    config.setProperty("delayBeforeNextRunFactor", this.getDelayBeforeNextRunFactor());
    config.setProperty(
        "maxDurationBeforeTtlExpireInSeconds", this.getMaxDurationBeforeTtlExpireInSeconds());

    Optional.ofNullable(this.getTlsKeyStorePath())
        .map(Objects::toString)
        .ifPresent(
            v -> {
              config.setProperty("tlsKeyStorePath", v);
            });

    Optional.ofNullable(this.getTlsTrustStorePath())
        .map(Objects::toString)
        .ifPresent(
            v -> {
              config.setProperty("tlsTrustStorePath", v);
            });

    return config;
  }
}
