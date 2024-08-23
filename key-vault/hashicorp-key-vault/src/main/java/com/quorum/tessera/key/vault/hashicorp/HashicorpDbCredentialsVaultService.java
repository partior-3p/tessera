package com.quorum.tessera.key.vault.hashicorp;

import com.quorum.tessera.config.KeyVaultConfig;
import com.quorum.tessera.key.vault.DbCredentials;
import com.quorum.tessera.key.vault.DbCredentialsVaultService;
import java.util.Map;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.support.VaultResponse;

public class HashicorpDbCredentialsVaultService implements DbCredentialsVaultService {

  private final VaultOperations vaultOperations;

  private String dbSecretEngineName = "database";
  private String vaultDbRole;
  private String credentialType = "static";

  private String credentialPath = "static-creds";

  HashicorpDbCredentialsVaultService(
      VaultOperations vaultOperations, KeyVaultConfig keyVaultConfig) {

    this.vaultOperations = vaultOperations;
    this.vaultDbRole = keyVaultConfig.getProperty("vaultDbRole").get();

    if (keyVaultConfig.hasProperty("dbSecretEngineName")
        && keyVaultConfig.getProperty("dbSecretEngineName").isPresent()) {
      this.dbSecretEngineName = keyVaultConfig.getProperty("dbSecretEngineName").get();
    }

    if (keyVaultConfig.hasProperty("credentialType")
        && keyVaultConfig.getProperty("credentialType").isPresent()) {
      this.credentialType = keyVaultConfig.getProperty("credentialType").get();
    }

    if ("dynamic".equals(this.credentialType)) {
      this.credentialPath = "creds";
    } else {
      this.credentialPath = "static-creds";
    }
  }

  public HashicorpDbCredentials getDbCredentials() {
    var credentials = new HashicorpDbCredentials();
    VaultResponse response;
    try {

      response =
          vaultOperations.read(
              String.format("%s/%s/%s", dbSecretEngineName, credentialPath, vaultDbRole));
    } catch (Exception ex) {
      throw new HashicorpDbCredentialsVaultException(
          "Unexpected error reading db credentials from hashicorp vault", ex);
    }

    if (response != null) {
      if (response.getData() != null) {
        Map<String, Object> data = response.getData();
        credentials.setUsername(data.get("username").toString());
        credentials.setPassword(data.get("password").toString());
        credentials.setLeaseDurationInSec(
            response.getData().containsKey("ttl")
                ? Long.valueOf((Integer) response.getData().get("ttl"))
                : response.getLeaseDuration());
      }
    } else {
      throw new HashicorpDbCredentialsVaultException(
          "Empty response from Hashicorp vault while trying to retrieve database credentials.");
    }
    return credentials;
  }

  public static class HashicorpDbCredentials implements DbCredentials {

    private String username;
    private String password;
    private long leaseDurationInSec;

    void setUsername(String username) {
      this.username = username;
    }

    void setPassword(String password) {
      this.password = password;
    }

    void setLeaseDurationInSec(long leaseDurationInSec) {
      this.leaseDurationInSec = leaseDurationInSec;
    }

    public String getUsername() {
      return username;
    }

    public String getPassword() {
      return password;
    }

    public long getLeaseDurationInSec() {
      return leaseDurationInSec;
    }
  }
}
