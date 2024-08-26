package com.quorum.tessera.test.vault.hashicorp;

import static java.util.concurrent.TimeUnit.SECONDS;

import com.quorum.tessera.test.util.ElUtil;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HashicorpDbSecretEngineCommands {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(HashicorpDbSecretEngineCommands.class);

  public void startPostgreSqlServer() {
    try {
      stopPostgreSqlServer();
      var initSqlPath = createPostgreSqlInitFileAndGetPath();
      var args =
          List.of(
              "docker",
              "run",
              "--name",
              "test-postgres",
              "-e",
              "POSTGRES_USER=testadmin",
              "-e",
              "POSTGRES_PASSWORD=testadmin",
              "-e",
              "POSTGRES_DB=tesseradb",
              "-p",
              "5432:5432",
              "-v",
              initSqlPath + ":/docker-entrypoint-initdb.d/init.sql",
              "-d",
              "postgres:latest");
      ProcessHandler command = new ProcessHandler(args);
      command.start();
      command.waitForCompletion();
    } catch (Exception ex) {
      LOGGER.error("Unexpected error while starting PostgreSql server", ex);
      throw new TestRuntimeException(ex);
    }
  }

  private String createPostgreSqlInitFileAndGetPath() {
    var sqlInitFilePath =
        ElUtil.createTempFileFromTemplate(
            getClass().getResource("/vault/postgres-init.sql"), Map.of());
    return sqlInitFilePath.toString();
  }

  public void stopPostgreSqlServer() {
    try {
      var command1 = new ProcessHandler(List.of("docker", "stop", "test-postgres"));
      command1.start();

      var command2 = new ProcessHandler(List.of("docker", "rm", "test-postgres"));
      command2.start();

      var command3 = new ProcessHandler(List.of("docker", "volume", "prune", "-f"));
      command3.start();

      List.of(command1, command2, command2)
          .forEach(
              cmd -> {
                try {
                  cmd.waitForCompletion(Duration.of(10, ChronoUnit.SECONDS));
                } catch (Exception ex) {
                  LOGGER.warn("Unexpected error. Continuing ..., details: ", ex);
                }
              });

    } catch (Exception ex) {
      LOGGER.error("stopPostgreSqlServer", ex);
      throw new TestRuntimeException(ex);
    }
  }

  public void waitForPostgreSqlServerToBeOnline() {
    // PostgreSQL database credentials
    String url = "jdbc:postgresql://localhost:5432/tesseradb";
    String user = "testtest";
    String password = "testtest";
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
