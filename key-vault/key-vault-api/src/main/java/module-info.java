module tessera.keyvault.api {
  requires tessera.config;
  requires tessera.shared;

  uses com.quorum.tessera.key.vault.KeyVaultServiceFactory;
  uses com.quorum.tessera.key.vault.DbCredentialsVaultServiceFactory;

  exports com.quorum.tessera.key.vault;
}
