package com.quorum.tessera.key.vault.hashicorp;

public class HashicorpDbCredentialsVaultException extends RuntimeException {

  HashicorpDbCredentialsVaultException(String message) {
    super(message);
  }

  HashicorpDbCredentialsVaultException(String message, Throwable cause) {
    super(message, cause);
  }
}
