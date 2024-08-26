package com.quorum.tessera.test.vault.hashicorp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import org.assertj.core.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessHandler.class);

  private final List<String> command;
  private Process process;
  private final ExecutorService executorService;

  public ProcessHandler(List<String> command) {
    this.command = command;
    this.executorService = Executors.newSingleThreadExecutor();
  }

  // Start the process with the given command arguments
  public void start() throws IOException {
    LOGGER.info("Starting process with command: {}", Strings.join(command).with(" "));
    ProcessBuilder processBuilder = new ProcessBuilder(command);
    processBuilder.redirectErrorStream(true); // Combine stdout and stderr
    this.process = processBuilder.start();
  }

  public int waitForCompletion() throws InterruptedException {
    captureConsoleOutput(process.getInputStream(), System.out);
    return process.waitFor();
  }

  // Wait for the process to complete with a timeout and output its console stream to the parent
  // process
  public int waitForCompletion(Duration timeout)
      throws TimeoutException, InterruptedException, ExecutionException, IOException {
    // Capture output and print to parent console in real-time
    if (!process.isAlive()) {
      LOGGER.info("Process has already been terminiated. Info: {}", process);
      return 0;
    }

    Future<Integer> future =
        executorService.submit(
            () -> {
              try {
                captureConsoleOutput(process.getInputStream(), System.out);
                return process.waitFor();
              } catch (InterruptedException e) {
                throw new TestRuntimeException("Process interrupted", e);
              }
            });

    try {
      return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      process.destroy(); // Kill the process on timeout
      throw new TimeoutException("Process timed out and was terminated.");
    } finally {
      executorService.shutdown();
    }
  }

  // Capture and print the console output of the process
  private void captureConsoleOutput(InputStream inputStream, Appendable out) {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
      String line;
      while ((line = reader.readLine()) != null) {
        out.append(line).append(System.lineSeparator());
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  // Check if a pattern matches in the console output, terminate process if found
  public boolean checkForPatternAndTerminate(Pattern pattern) throws IOException {
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(process.getInputStream()))) {
      String line;
      while ((line = reader.readLine()) != null) {
        System.out.println(line);
        if (pattern.matcher(line).find()) {
          System.out.println("Pattern matched: Terminating process...");
          process.destroy();
          return true;
        }
      }
    }
    return false;
  }
}
