package com.quorum.tessera.key.vault.hashicorp;

public class HashicorpDbCredentialsVaultException extends RuntimeException {
  HashicorpDbCredentialsVaultException(Throwable cause) {
    super(cause);
  }

  HashicorpDbCredentialsVaultException(String message) {
    super(message);
  }

  HashicorpDbCredentialsVaultException(String message, Throwable cause) {
    super(message, cause);
  }
}
