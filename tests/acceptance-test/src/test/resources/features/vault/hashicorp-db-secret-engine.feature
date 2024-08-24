Feature: Hashicorp Vault support With DB Secret Engine
Storing and retrieving Tessera public/private key pairs from a Hashicorp Vault

  Background:
    Given the vault server has been started with TLS-enabled
    And the vault is initialised and unsealed
    And the vault has a v2 kv secret engine
    And the vault has a transit secret engine
    And the vault has transit secret key

  Scenario:
    Given PostgeSql server started
  Scenario: Tessera retrieves a key pair from the Vault using the Token auth method
    Given the vault contains a key pair
    And the configfile contains the correct vault configuration
    And the configfile contains the correct key data
    When Tessera is started with the following CLI args and token environment variable
    """
    -configfile %s -pidfile %s -o jdbc.autoCreateTables=true
    """
    Then Tessera will retrieve the key pair from the vault
