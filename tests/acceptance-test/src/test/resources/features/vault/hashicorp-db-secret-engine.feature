Feature: Hashicorp Vault support with DB Secret Engine

  Background:
    Given the vault server has been started with TLS-enabled
    Given PostgeSql server started
    And the vault is initialised and unsealed
    And the vault has a v2 kv secret engine
    And the vault has a database secret engine

  Scenario: Tessera connects to PostgeSql database using credentials from the Vault DB Secret Engine and retrieves a key pair from the Vault
    Given the vault contains a key pair
    And the AppRole auth method is enabled at the default path
    And the configfile is created that contains the postgresql settings
    And the configfile contains the correct vault configuration
    And the configfile contains the correct key data
    Then Tessera is started with the following CLI args and approle environment variables
    """
    -configfile %s -pidfile %s -o jdbc.autoCreateTables=true
    """
    Then Tessera will retrieve the key pair from the vault
    Then Tessera will fetch a missing transaction and successfully get a response of not found
