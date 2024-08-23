package com.quorum.tessera.config;

public class ConfigException extends RuntimeException {

  public ConfigException(final Throwable cause) {
    super(cause);
  }

  public ConfigException(final String message) {
    super(message);
  }
}
