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

  public HashicorpVaultDbCredentialsConfig(
      String url,
      String namespace,
      String approlePath,
      String dbSecretEngineName,
      String vaultDbRole,
      Path tlsKeyStorePath,
      Path tlsTrustStorePath) {
    this.url = url;
    this.namespace = namespace;
    this.approlePath = approlePath;
    this.dbSecretEngineName = dbSecretEngineName;
    this.vaultDbRole = vaultDbRole;
    this.tlsKeyStorePath = tlsKeyStorePath;
    this.tlsTrustStorePath = tlsTrustStorePath;
  }

  public HashicorpVaultDbCredentialsConfig() {}

  public String getUrl() {
    return this.url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getNamespace() {
    return namespace;
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  public String getDbSecretEngineName() {
    if (dbSecretEngineName == null) {
      return "database";
    }
    return dbSecretEngineName;
  }

  public void setDbSecretEngineName(String dbSecretEngineName) {
    this.dbSecretEngineName = dbSecretEngineName;
  }

  public String getVaultDbRole() {
    return vaultDbRole;
  }

  public void setVaultDbRole(String vaultDbRole) {
    this.vaultDbRole = vaultDbRole;
  }

  public Path getTlsKeyStorePath() {
    return tlsKeyStorePath;
  }

  public void setTlsKeyStorePath(Path tlsKeyStorePath) {
    this.tlsKeyStorePath = tlsKeyStorePath;
  }

  public Path getTlsTrustStorePath() {
    return tlsTrustStorePath;
  }

  public void setTlsTrustStorePath(Path tlsTrustStorePath) {
    this.tlsTrustStorePath = tlsTrustStorePath;
  }

  public String getApprolePath() {
    if (approlePath == null) {
      return "approle";
    }
    return approlePath;
  }

  public void setApprolePath(String approlePath) {
    this.approlePath = approlePath;
  }

  public KeyVaultType getKeyVaultType() {
    return KeyVaultType.HASHICORP;
  }

  public DefaultKeyVaultConfig toKeyVaultConfig(){
    DefaultKeyVaultConfig config = new DefaultKeyVaultConfig();
    config.setKeyVaultType(this.getKeyVaultType());
    config.setProperty("url", this.getUrl());
    config.setProperty("approlePath", this.getApprolePath());
    config.setProperty("dbSecretEngineName", this.getDbSecretEngineName());
    config.setProperty("vaultDbRole", this.getVaultDbRole());

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
