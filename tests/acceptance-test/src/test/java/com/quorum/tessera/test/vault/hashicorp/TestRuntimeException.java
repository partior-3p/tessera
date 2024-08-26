package com.quorum.tessera.test.vault.hashicorp;

public class TestRuntimeException extends RuntimeException {
  public TestRuntimeException(Throwable throwable) {
    super(throwable);
  }

  public TestRuntimeException(String message) {
    super(message);
  }

  public TestRuntimeException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
