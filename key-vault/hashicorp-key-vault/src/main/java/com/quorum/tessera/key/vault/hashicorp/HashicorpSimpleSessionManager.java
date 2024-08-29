package com.quorum.tessera.key.vault.hashicorp;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.LoginToken;
import org.springframework.vault.authentication.SessionManager;
import org.springframework.vault.support.VaultToken;

public class HashicorpSimpleSessionManager implements SessionManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(HashicorpSimpleSessionManager.class);
  private final ClientAuthentication clientAuthentication;

  private final ReentrantLock lock = new ReentrantLock();

  private volatile Optional<VaultToken> token = Optional.empty();
  private Instant tokenStartTime;

  private final Duration safetyMarginInSeconds = Duration.ofSeconds(5);

  public HashicorpSimpleSessionManager(ClientAuthentication clientAuthentication) {

    Assert.notNull(clientAuthentication, "ClientAuthentication must not be null");

    this.clientAuthentication = clientAuthentication;
  }

  private boolean isTokenEmptyOrExpired() {
    if (this.token.isEmpty()) {
      return true;
    }
    if (this.token.get() instanceof LoginToken) {
      LoginToken loginToken = (LoginToken) this.token.get();
      Duration ttlAdjusted = loginToken.getLeaseDuration().minus(safetyMarginInSeconds);
      return Duration.between(tokenStartTime, Instant.now()).getSeconds()
          >= ttlAdjusted.getSeconds();
    }
    return false;
  }

  @Override
  public VaultToken getSessionToken() {

    if (isTokenEmptyOrExpired()) {
      this.lock.lock();
      try {
        if (isTokenEmptyOrExpired()) {
          this.token = Optional.of(this.clientAuthentication.login());
          this.tokenStartTime = Instant.now();
          LOGGER.info("Successfully retrieved new vault token.");
        }
      } finally {
        this.lock.unlock();
      }
    }

    return this.token.orElseThrow(() -> new IllegalStateException("Cannot obtain VaultToken"));
  }
}
