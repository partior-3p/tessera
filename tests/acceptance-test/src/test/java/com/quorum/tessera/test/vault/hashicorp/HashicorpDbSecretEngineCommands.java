package com.quorum.tessera.test.vault.hashicorp;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HashicorpDbSecretEngineCommands {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(HashicorpDbSecretEngineCommands.class);

  public void startPostgreSqlServer() {
    try {
      var args =
          List.of(
              "docker",
              "run",
              "--name",
              "test-postgres",
              "-e",
              "POSTGRES_USER=test",
              "-e",
              "POSTGRES_PASSWORD=test",
              "-e",
              "POSTGRES_DB=testdb",
              "-p",
              "6000:5432",
              "-d",
              "postgres:latest");
      ProcessHandler command = new ProcessHandler(args);
      command.start();
      command.waitForCompletion();
    } catch (Exception ex) {
      LOGGER.error("Unexpected error while starting PostgreSql server", ex);
      throw new RuntimeException(ex);
    }
  }

  public void stopPostgreSqlServer() {
    try {
      ProcessHandler command = new ProcessHandler(List.of("docker", "stop", "test-postgres"));
      command.start();
      command.waitForCompletion();

      command = new ProcessHandler(List.of("docker", "rm", "test-postgres"));
      command.start();
      command.waitForCompletion();
    } catch (Exception ex) {
      LOGGER.error("stopPostgreSqlServer", ex);
      throw new RuntimeException(ex);
    }
  }

  public void waitForPostgreSqlServerToBeOnline() {
    // PostgreSQL database credentials
    String url = "jdbc:postgresql://localhost:6000/testdb";
    String user = "test";
    String password = "test";
    AtomicReference<Connection> connection = new AtomicReference<>();
    AtomicReference<Integer> counter = new AtomicReference<>(0);

    LOGGER.info("Trying for connection!");
    // Establish connection
    Awaitility.await()
        .atLeast(1, SECONDS) // Maximum wait time
        .atMost(10, SECONDS)
        .pollInterval(1, SECONDS) // Time between each retry
        .ignoreExceptions()
        .until(
            () -> {
              LOGGER.info("Attempt {} ...", counter.get());
              counter.set(counter.get() + 1);
              connection.set(DriverManager.getConnection(url, user, password));
              LOGGER.info("Connection successful!");
              return true;
            });

    // Close the connection when done
    if (connection.get() != null) {
      try {
        connection.get().close();
        LOGGER.info("Connection closed.");
      } catch (SQLException e) {
        LOGGER.error("Unexpected error while closing connection.", e);
      }
    }
  }
}
