Feature: Hashicorp Vault support With DB Secret Engine
Storing and retrieving Tessera public/private key pairs from a Hashicorp Vault

  Background:
    Given the vault server has been started with TLS-enabled
    Given PostgeSql server started
    And the vault is initialised and unsealed
    And the vault has a v2 kv secret engine
    And the vault has a database secret engine

  Scenario: Tessera retrieves a key pair from the Vault using the default AppRole auth method without having to specify the default path
    Given the vault contains a key pair
    And the AppRole auth method is enabled at the default path
    And the configfile is created that contains the postgresql settings
    And the configfile contains the correct vault configuration
    And the configfile contains the correct key data
    When Tessera is started with the following CLI args and approle environment variables
    """
    -configfile %s -pidfile %s -o jdbc.autoCreateTables=true
    """
    Then Tessera will retrieve the key pair from the vault
