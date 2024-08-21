package com.quorum.tessera.key.vault;

public interface DbCredentials {
  String getUsername();

  String getPassword();

  long getLeaseDurationInSec();
}
