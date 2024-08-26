package com.quorum.tessera.test.vault.hashicorp;

import static com.quorum.tessera.config.util.EnvironmentVariables.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.quorum.tessera.config.Config;
import com.quorum.tessera.config.HashicorpKeyVaultConfig;
import com.quorum.tessera.config.util.JaxbUtil;
import com.quorum.tessera.test.util.ElUtil;
import exec.ExecArgsBuilder;
import exec.NodeExecManager;
import io.cucumber.java8.En;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.ws.rs.core.UriBuilder;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.net.ssl.HttpsURLConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HashicorpStepDefs implements En {

  private static final Logger LOGGER = LoggerFactory.getLogger(HashicorpStepDefs.class);

  private final ExecutorService executorService = Executors.newCachedThreadPool();

  private String vaultToken;

  private String unsealKey;

  private final String secretEngineName = "kv";

  private final String transitSecretEngineName = "transit";

  private final String databaseSecretEngineName = "business/database";

  private final String transitKeyName = "tessera-tse-key";

  private String approleRoleId;

  private String approleSecretId;

  private Path tempTesseraConfig;

  private Path tempTesseraWithTseConfig;

  private final AtomicReference<Process> tesseraProcess = new AtomicReference<>();

  private final HashicorpDbSecretEngineCommands hashicorpDbSecretEngineCommands =
      new HashicorpDbSecretEngineCommands();

  public HashicorpStepDefs() {
    final AtomicReference<Process> vaultServerProcess = new AtomicReference<>();

    Before(
        () -> {
          // only needed when running outside of maven build process
          //            System.setProperty("application.jar",
          // "/Users/yourname/jpmc-tessera/tessera-app/target/tessera-app-0.11-SNAPSHOT-app.jar");

          tempTesseraConfig = null;
          tempTesseraWithTseConfig = null;
        });

    Given(
        "^the vault server has been started with TLS-enabled$",
        () -> {
          Path vaultDir =
              Files.createDirectories(
                  Paths.get("target/temp/" + UUID.randomUUID().toString() + "/vault"));

          Map<String, Object> params = new HashMap<>();
          params.put("vaultPath", vaultDir.toString());
          params.put("vaultCert", getServerTlsCert());
          params.put("vaultKey", getServerTlsKey());
          params.put("clientCert", getClientCaTlsCert());

          Path configFile =
              ElUtil.createTempFileFromTemplate(
                  getClass().getResource("/vault/hashicorp-tls-config.hcl"), params);

          List<String> args = Arrays.asList("vault", "server", "-config=" + configFile.toString());
          System.out.println(String.join(" ", args));

          ProcessBuilder vaultServerProcessBuilder = new ProcessBuilder(args);

          vaultServerProcess.set(vaultServerProcessBuilder.redirectErrorStream(true).start());

          AtomicBoolean isAddressAlreadyInUse = new AtomicBoolean(false);

          executorService.submit(
              () -> {
                try (BufferedReader reader =
                    Stream.of(vaultServerProcess.get().getInputStream())
                        .map(InputStreamReader::new)
                        .map(BufferedReader::new)
                        .findAny()
                        .get()) {

                  String line;
                  while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                    if (line.matches("^Error.+address already in use")) {
                      isAddressAlreadyInUse.set(true);
                    }
                  }

                } catch (IOException ex) {
                  throw new UncheckedIOException(ex);
                }
              });

          // wait so that assertion is not evaluated before output is checked
          CountDownLatch startUpLatch = new CountDownLatch(1);
          startUpLatch.await(5, TimeUnit.SECONDS);

          assertThat(isAddressAlreadyInUse).isFalse();

          setKeyStoreProperties();

          // Initialise the vault
          final URL initUrl =
              UriBuilder.fromUri("https://localhost:8200").path("v1/sys/init").build().toURL();
          HttpsURLConnection initUrlConnection = (HttpsURLConnection) initUrl.openConnection();

          initUrlConnection.setDoOutput(true);
          initUrlConnection.setRequestMethod("PUT");

          String initData = "{\"secret_shares\": 1, \"secret_threshold\": 1}";

          try (OutputStreamWriter writer =
              new OutputStreamWriter(initUrlConnection.getOutputStream())) {
            writer.write(initData);
          }

          initUrlConnection.connect();
          assertThat(initUrlConnection.getResponseCode()).isEqualTo(HttpsURLConnection.HTTP_OK);

          JsonReader initResponseReader = Json.createReader(initUrlConnection.getInputStream());

          JsonObject initResponse = initResponseReader.readObject();

          assertThat(initResponse.getString("root_token")).isNotEmpty();
          vaultToken = initResponse.getString("root_token");

          assertThat(initResponse.getJsonArray("keys_base64").size()).isEqualTo(1);
          assertThat(initResponse.getJsonArray("keys_base64").get(0).toString()).isNotEmpty();
          String quotedUnsealKey = initResponse.getJsonArray("keys_base64").get(0).toString();

          if ('\"' == quotedUnsealKey.charAt(0)
              && '\"' == quotedUnsealKey.charAt(quotedUnsealKey.length() - 1)) {
            unsealKey = quotedUnsealKey.substring(1, quotedUnsealKey.length() - 1);
          }

          // Unseal the vault
          final URL unsealUrl =
              UriBuilder.fromUri("https://localhost:8200").path("v1/sys/unseal").build().toURL();
          HttpsURLConnection unsealUrlConnection = (HttpsURLConnection) unsealUrl.openConnection();

          unsealUrlConnection.setDoOutput(true);
          unsealUrlConnection.setRequestMethod("PUT");

          String unsealData = "{\"key\": \"" + unsealKey + "\"}";

          try (OutputStreamWriter writer =
              new OutputStreamWriter(unsealUrlConnection.getOutputStream())) {
            writer.write(unsealData);
          }

          unsealUrlConnection.connect();
          assertThat(unsealUrlConnection.getResponseCode()).isEqualTo(HttpsURLConnection.HTTP_OK);
        });

    Given(
        "the vault is initialised and unsealed",
        () -> {
          final URL initUrl =
              UriBuilder.fromUri("https://localhost:8200").path("v1/sys/health").build().toURL();
          HttpsURLConnection initUrlConnection = (HttpsURLConnection) initUrl.openConnection();
          initUrlConnection.connect();

          // See https://www.vaultproject.io/api/system/health.html for info on response codes for
          // this
          // endpoint
          assertThat(initUrlConnection.getResponseCode())
              .as("check vault is initialized")
              .isNotEqualTo(HttpsURLConnection.HTTP_NOT_IMPLEMENTED);
          assertThat(initUrlConnection.getResponseCode())
              .as("check vault is unsealed")
              .isNotEqualTo(503);
          assertThat(initUrlConnection.getResponseCode()).isEqualTo(HttpsURLConnection.HTTP_OK);
        });

    Given(
        "the vault has a v2 kv secret engine",
        () -> {
          setKeyStoreProperties();

          // Create new v2 kv secret engine
          final String mountPath = String.format("v1/sys/mounts/%s", secretEngineName);
          final URL createSecretEngineUrl =
              UriBuilder.fromUri("https://localhost:8200").path(mountPath).build().toURL();
          HttpsURLConnection createSecretEngineUrlConnection =
              (HttpsURLConnection) createSecretEngineUrl.openConnection();

          createSecretEngineUrlConnection.setDoOutput(true);
          createSecretEngineUrlConnection.setRequestMethod("POST");
          createSecretEngineUrlConnection.setRequestProperty("X-Vault-Token", vaultToken);

          final String createSecretEngineData =
              "{\"type\": \"kv\", \"options\": {\"version\": \"2\"}}";

          try (OutputStreamWriter writer =
              new OutputStreamWriter(createSecretEngineUrlConnection.getOutputStream())) {
            writer.write(createSecretEngineData);
          }

          createSecretEngineUrlConnection.connect();
          assertThat(createSecretEngineUrlConnection.getResponseCode())
              .isEqualTo(HttpsURLConnection.HTTP_NO_CONTENT);
        });

    Given(
        "the vault has a transit secret engine",
        () -> {
          setKeyStoreProperties();

          // Create new v2 kv secret engine
          final String mountPath = String.format("v1/sys/mounts/%s", transitSecretEngineName);
          final URL createSecretEngineUrl =
              UriBuilder.fromUri("https://localhost:8200").path(mountPath).build().toURL();
          HttpsURLConnection createSecretEngineUrlConnection =
              (HttpsURLConnection) createSecretEngineUrl.openConnection();

          createSecretEngineUrlConnection.setDoOutput(true);
          createSecretEngineUrlConnection.setRequestMethod("POST");
          createSecretEngineUrlConnection.setRequestProperty("X-Vault-Token", vaultToken);

          final String createSecretEngineData = "{\"type\": \"transit\"}";

          try (OutputStreamWriter writer =
              new OutputStreamWriter(createSecretEngineUrlConnection.getOutputStream())) {
            writer.write(createSecretEngineData);
          }

          createSecretEngineUrlConnection.connect();
          assertThat(createSecretEngineUrlConnection.getResponseCode())
              .isEqualTo(HttpsURLConnection.HTTP_NO_CONTENT);
        });

    Given(
        "the vault has a database secret engine",
        () -> {
          setKeyStoreProperties();

          final Consumer<Integer> RESPONSE_HTTP_OK =
              (value) -> assertThat(value).isEqualTo(HttpsURLConnection.HTTP_OK);
          final Consumer<Integer> RESPONSE_HTTP_NO_CONTENT =
              (value) -> assertThat(value).isEqualTo(HttpsURLConnection.HTTP_NO_CONTENT);

          var response =
              makeHttpRequestAndGetResponse(
                  "https://localhost:8200",
                  String.format("v1/sys/mounts/%s", databaseSecretEngineName),
                  "POST",
                  "{\"type\": \"database\"}",
                  Map.of("X-Vault-Token", vaultToken));

          assertThat(response.getResponseCode())
              .satisfiesAnyOf(RESPONSE_HTTP_OK, RESPONSE_HTTP_NO_CONTENT);

          response =
              makeHttpRequestAndGetResponse(
                  "https://localhost:8200",
                  String.format("v1/%s/config/tessera-conn", databaseSecretEngineName),
                  "POST",
                  "{\n"
                      + "        \"plugin_name\": \"postgresql-database-plugin\",\n"
                      + "        \"allowed_roles\": [\"tessera-db-role\", \"tessera-db-static-role\"],\n"
                      + "        \"connection_url\": \"postgresql://{{username}}:{{password}}@localhost:5432/tesseradb\",\n"
                      + "        \"username\": \"testadmin\",\n"
                      + "        \"password\": \"testadmin\",\n"
                      + "        \"password_authentication\": \"password\",\n"
                      + "        \"verify_connection\": \"false\"\n"
                      + "     }",
                  Map.of("X-Vault-Token", vaultToken));

          assertThat(response.getResponseCode())
              .satisfiesAnyOf(RESPONSE_HTTP_OK, RESPONSE_HTTP_NO_CONTENT);

          response =
              makeHttpRequestAndGetResponse(
                  "https://localhost:8200",
                  String.format("v1/%s/roles/tessera-db-role", databaseSecretEngineName),
                  "POST",
                  "{\n"
                      + "        \"db_name\": \"tessera-conn\",\n"
                      + "        \"creation_statements\": \"CREATE ROLE \\\"{{name}}\\\" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}';  GRANT \\\"testtest\\\" TO \\\"{{name}}\\\";\",\n"
                      + "        \"default_ttl\": \"60s\",\n"
                      + "        \"max_ttl\": \"120s\"\n"
                      + "     }",
                  Map.of("X-Vault-Token", vaultToken));

          assertThat(response.getResponseCode())
              .satisfiesAnyOf(RESPONSE_HTTP_OK, RESPONSE_HTTP_NO_CONTENT);

          response =
              makeHttpRequestAndGetResponse(
                  "https://localhost:8200",
                  String.format(
                      "v1/%s/static-roles/tessera-db-static-role", databaseSecretEngineName),
                  "POST",
                  "{\n"
                      + "        \"db_name\": \"tessera-conn\",\n"
                      + "        \"username\": \"testtest\",\n"
                      + "        \"rotation_period\": \"60s\"\n"
                      + "     }",
                  Map.of("X-Vault-Token", vaultToken));

          assertThat(response.getResponseCode())
              .satisfiesAnyOf(RESPONSE_HTTP_OK, RESPONSE_HTTP_NO_CONTENT);
        });

    Given(
        "the vault has transit secret key",
        () -> {
          setKeyStoreProperties();

          // Create new v2 kv secret engine
          final String transitKeyPath = "v1/transit/keys/" + transitKeyName;
          final URL createSecretEngineUrl =
              UriBuilder.fromUri("https://localhost:8200").path(transitKeyPath).build().toURL();
          HttpsURLConnection createSecretEngineUrlConnection =
              (HttpsURLConnection) createSecretEngineUrl.openConnection();

          createSecretEngineUrlConnection.setDoOutput(true);
          createSecretEngineUrlConnection.setRequestMethod("POST");
          createSecretEngineUrlConnection.setRequestProperty("X-Vault-Token", vaultToken);

          final String keyType = "{\"type\": \"aes256-gcm96\"}";

          try (OutputStreamWriter writer =
              new OutputStreamWriter(createSecretEngineUrlConnection.getOutputStream())) {
            writer.write(keyType);
          }

          createSecretEngineUrlConnection.connect();
          assertThat(createSecretEngineUrlConnection.getResponseCode())
              .isEqualTo(HttpsURLConnection.HTTP_NO_CONTENT);
        });

    Given(
        "^the AppRole auth method is enabled at (?:the|a) (default|custom) path$",
        (String approleType) -> {
          setKeyStoreProperties();

          String approlePath;

          if ("default".equals(approleType)) {
            approlePath = "approle";
          } else {
            approlePath = "different-approle";
          }

          // Enable approle authentication
          final URL enableApproleUrl =
              UriBuilder.fromUri("https://localhost:8200")
                  .path("v1/sys/auth/" + approlePath)
                  .build()
                  .toURL();
          HttpsURLConnection enableApproleUrlConnection =
              (HttpsURLConnection) enableApproleUrl.openConnection();

          enableApproleUrlConnection.setDoOutput(true);
          enableApproleUrlConnection.setRequestMethod("POST");
          enableApproleUrlConnection.setRequestProperty("X-Vault-Token", vaultToken);

          String enableApproleData = "{\"type\": \"approle\"}";

          try (OutputStreamWriter writer =
              new OutputStreamWriter(enableApproleUrlConnection.getOutputStream())) {
            writer.write(enableApproleData);
          }

          enableApproleUrlConnection.connect();
          assertThat(enableApproleUrlConnection.getResponseCode())
              .isEqualTo(HttpsURLConnection.HTTP_NO_CONTENT);

          // Create a policy and assign to a new approle
          final URL createPolicyUrl =
              UriBuilder.fromUri("https://localhost:8200")
                  .path("v1/sys/policy/simple-policy")
                  .build()
                  .toURL();
          HttpsURLConnection createPolicyUrlConnection =
              (HttpsURLConnection) createPolicyUrl.openConnection();

          createPolicyUrlConnection.setDoOutput(true);
          createPolicyUrlConnection.setRequestMethod("POST");
          createPolicyUrlConnection.setRequestProperty("X-Vault-Token", vaultToken);

          final String createPolicyData =
              String.format(
                  "{ \"policy\": \"path \\\"%s/data/tessera*\\\" { capabilities = [\\\"create\\\", \\\"update\\\", \\\"read\\\"]}\\n"
                      + "path \\\"%s/*\\\" { capabilities = [\\\"create\\\", \\\"update\\\", \\\"read\\\",\\\"delete\\\", \\\"list\\\", \\\"sudo\\\"]}\\n"
                      + "path \\\"%s/static-roles/*\\\" { capabilities = [\\\"create\\\", \\\"update\\\", \\\"read\\\",\\\"delete\\\", \\\"list\\\"]}\\n"
                      + "path \\\"%s/roles/*\\\" { capabilities = [\\\"create\\\", \\\"update\\\", \\\"read\\\",\\\"delete\\\", \\\"list\\\"]}\\n"
                      + "path \\\"transit/encrypt/%s\\\" { capabilities = [\\\"create\\\", \\\"update\\\"]}\\n"
                      + "path \\\"transit/decrypt/%s\\\" { capabilities = [\\\"create\\\", \\\"update\\\"]}\\n"
                      + "\" }",
                  secretEngineName,
                  databaseSecretEngineName,
                  databaseSecretEngineName,
                  databaseSecretEngineName,
                  transitKeyName,
                  transitKeyName);

          try (OutputStreamWriter writer =
              new OutputStreamWriter(createPolicyUrlConnection.getOutputStream())) {
            writer.write(createPolicyData);
          }

          createPolicyUrlConnection.connect();
          assertThat(createPolicyUrlConnection.getResponseCode())
              .isEqualTo(HttpsURLConnection.HTTP_NO_CONTENT);

          final URL createApproleUrl =
              UriBuilder.fromUri("https://localhost:8200")
                  .path("v1/auth/" + approlePath + "/role/simple-role")
                  .build()
                  .toURL();
          HttpsURLConnection createApproleUrlConnection =
              (HttpsURLConnection) createApproleUrl.openConnection();

          createApproleUrlConnection.setDoOutput(true);
          createApproleUrlConnection.setRequestMethod("POST");
          createApproleUrlConnection.setRequestProperty("X-Vault-Token", vaultToken);

          final String createApproleData = "{ \"policies\": [\"simple-policy\"] }";

          try (OutputStreamWriter writer =
              new OutputStreamWriter(createApproleUrlConnection.getOutputStream())) {
            writer.write(createApproleData);
          }

          createApproleUrlConnection.connect();
          assertThat(createApproleUrlConnection.getResponseCode())
              .isEqualTo(HttpsURLConnection.HTTP_NO_CONTENT);

          // Retrieve approle credentials
          final URL getRoleIdUrl =
              UriBuilder.fromUri("https://localhost:8200")
                  .path("v1/auth/" + approlePath + "/role/simple-role/role-id")
                  .build()
                  .toURL();
          HttpsURLConnection getRoleIdUrlConnection =
              (HttpsURLConnection) getRoleIdUrl.openConnection();

          getRoleIdUrlConnection.setRequestProperty("X-Vault-Token", vaultToken);

          getRoleIdUrlConnection.connect();
          assertThat(getRoleIdUrlConnection.getResponseCode())
              .isEqualTo(HttpsURLConnection.HTTP_OK);

          JsonReader jsonReader = Json.createReader(getRoleIdUrlConnection.getInputStream());
          JsonObject getRoleIdObject = jsonReader.readObject().getJsonObject("data");

          assertThat(getRoleIdObject.getString("role_id")).isNotEmpty();
          approleRoleId = getRoleIdObject.getString("role_id");

          final URL createSecretIdUrl =
              UriBuilder.fromUri("https://localhost:8200")
                  .path("v1/auth/" + approlePath + "/role/simple-role/secret-id")
                  .build()
                  .toURL();
          HttpsURLConnection createSecretIdUrlConnection =
              (HttpsURLConnection) createSecretIdUrl.openConnection();

          createSecretIdUrlConnection.setRequestMethod("POST");
          createSecretIdUrlConnection.setRequestProperty("X-Vault-Token", vaultToken);

          createSecretIdUrlConnection.connect();
          assertThat(createSecretIdUrlConnection.getResponseCode())
              .isEqualTo(HttpsURLConnection.HTTP_OK);

          JsonReader anotherJsonReader =
              Json.createReader(createSecretIdUrlConnection.getInputStream());
          JsonObject createSecretIdObject = anotherJsonReader.readObject().getJsonObject("data");

          assertThat(createSecretIdObject.getString("secret_id")).isNotEmpty();
          approleSecretId = createSecretIdObject.getString("secret_id");

          createTempTesseraConfigWithApprole(approlePath);
        });

    Given(
        "the vault contains a key pair",
        () -> {
          Objects.requireNonNull(vaultToken);

          setKeyStoreProperties();

          // Set secret data
          final String setPath = String.format("v1/%s/data/tessera", secretEngineName);
          final URL setSecretUrl =
              UriBuilder.fromUri("https://localhost:8200").path(setPath).build().toURL();
          HttpsURLConnection setSecretUrlConnection =
              (HttpsURLConnection) setSecretUrl.openConnection();

          setSecretUrlConnection.setDoOutput(true);
          setSecretUrlConnection.setRequestMethod("POST");
          setSecretUrlConnection.setRequestProperty("X-Vault-Token", vaultToken);

          String setSecretData =
              "{\"data\": {\"publicKey\": \"/+UuD63zItL1EbjxkKUljMgG8Z1w0AJ8pNOR4iq2yQc=\", \"privateKey\": \"yAWAJjwPqUtNVlqGjSrBmr1/iIkghuOh1803Yzx9jLM=\"}}";

          try (OutputStreamWriter writer =
              new OutputStreamWriter(setSecretUrlConnection.getOutputStream())) {
            writer.write(setSecretData);
          }

          setSecretUrlConnection.connect();
          assertThat(setSecretUrlConnection.getResponseCode())
              .isEqualTo(HttpsURLConnection.HTTP_OK);

          final String getPath = String.format("v1/%s/data/tessera", secretEngineName);
          final URL getSecretUrl =
              UriBuilder.fromUri("https://localhost:8200").path(getPath).build().toURL();
          HttpsURLConnection getSecretUrlConnection =
              (HttpsURLConnection) getSecretUrl.openConnection();
          getSecretUrlConnection.setRequestProperty("X-Vault-Token", vaultToken);

          getSecretUrlConnection.connect();
          assertThat(getSecretUrlConnection.getResponseCode())
              .isEqualTo(HttpsURLConnection.HTTP_OK);

          JsonReader jsonReader = Json.createReader(getSecretUrlConnection.getInputStream());

          JsonObject getSecretObject = jsonReader.readObject();
          JsonObject keyDataObject = getSecretObject.getJsonObject("data").getJsonObject("data");
          assertThat(keyDataObject.getString("publicKey"))
              .isEqualTo("/+UuD63zItL1EbjxkKUljMgG8Z1w0AJ8pNOR4iq2yQc=");
          assertThat(keyDataObject.getString("privateKey"))
              .isEqualTo("yAWAJjwPqUtNVlqGjSrBmr1/iIkghuOh1803Yzx9jLM=");
        });

    Given(
        "the vault contains a key pair encrypted by TSE",
        () -> {
          Objects.requireNonNull(vaultToken);

          setKeyStoreProperties();

          // Set secret data
          final String setPath = String.format("v1/%s/data/tessera", secretEngineName);
          final URL setSecretUrl =
              UriBuilder.fromUri("https://localhost:8200").path(setPath).build().toURL();
          HttpsURLConnection setSecretUrlConnection =
              (HttpsURLConnection) setSecretUrl.openConnection();

          setSecretUrlConnection.setDoOutput(true);
          setSecretUrlConnection.setRequestMethod("POST");
          setSecretUrlConnection.setRequestProperty("X-Vault-Token", vaultToken);

          final String encryptedPublicKey =
              getTSEEncryptedValue("/+UuD63zItL1EbjxkKUljMgG8Z1w0AJ8pNOR4iq2yQc=");
          final String encryptedPrivateKey =
              getTSEEncryptedValue("yAWAJjwPqUtNVlqGjSrBmr1/iIkghuOh1803Yzx9jLM=");

          String setSecretData =
              "{\"data\": {\"publicKey\": \""
                  + encryptedPublicKey
                  + "\", \"privateKey\": \""
                  + encryptedPrivateKey
                  + "\"}}";

          try (OutputStreamWriter writer =
              new OutputStreamWriter(setSecretUrlConnection.getOutputStream())) {
            writer.write(setSecretData);
          }

          setSecretUrlConnection.connect();
          assertThat(setSecretUrlConnection.getResponseCode())
              .isEqualTo(HttpsURLConnection.HTTP_OK);

          final String getPath = String.format("v1/%s/data/tessera", secretEngineName);
          final URL getSecretUrl =
              UriBuilder.fromUri("https://localhost:8200").path(getPath).build().toURL();
          HttpsURLConnection getSecretUrlConnection =
              (HttpsURLConnection) getSecretUrl.openConnection();
          getSecretUrlConnection.setRequestProperty("X-Vault-Token", vaultToken);

          getSecretUrlConnection.connect();
          assertThat(getSecretUrlConnection.getResponseCode())
              .isEqualTo(HttpsURLConnection.HTTP_OK);

          JsonReader jsonReader = Json.createReader(getSecretUrlConnection.getInputStream());

          JsonObject getSecretObject = jsonReader.readObject();
          JsonObject keyDataObject = getSecretObject.getJsonObject("data").getJsonObject("data");
          assertThat(keyDataObject.getString("publicKey")).isEqualTo(encryptedPublicKey);
          assertThat(keyDataObject.getString("privateKey")).isEqualTo(encryptedPrivateKey);
        });

    Given(
        "^the configfile contains the correct vault configuration(| and custom approle configuration)",
        (String isCustomApprole) -> {
          createTempTesseraConfig();

          final Config config =
              JaxbUtil.unmarshal(Files.newInputStream(tempTesseraConfig), Config.class);

          HashicorpKeyVaultConfig expectedVaultConfig = new HashicorpKeyVaultConfig();
          expectedVaultConfig.setUrl("https://localhost:8200");
          expectedVaultConfig.setTlsKeyStorePath(Paths.get(getClientTlsKeystore()));
          expectedVaultConfig.setTlsTrustStorePath(Paths.get(getClientTlsTruststore()));

          if (!isCustomApprole.isEmpty()) {
            expectedVaultConfig.setApprolePath("different-approle");
          }

          assertThat(config.getKeys().getHashicorpKeyVaultConfig())
              .isEqualToComparingFieldByField(expectedVaultConfig);
        });

    Given(
        "^the configfile is created that contains the postgresql settings",
        () -> {
          createTempTesseraWithPostgreSqlAndSecretEngineConfig();
        });

    Given(
        "^the configfile contains the correct vault and TSE configuration",
        () -> {
          var originalTempTesseraConfig = tempTesseraConfig;
          try {
            createTempTesseraConfigWithApproleAndWithTSE();

            final Config config =
                JaxbUtil.unmarshal(Files.newInputStream(tempTesseraConfig), Config.class);

            HashicorpKeyVaultConfig expectedVaultConfig = new HashicorpKeyVaultConfig();
            expectedVaultConfig.setUrl("https://localhost:8200");
            expectedVaultConfig.setTlsKeyStorePath(Paths.get(getClientTlsKeystore()));
            expectedVaultConfig.setTlsTrustStorePath(Paths.get(getClientTlsTruststore()));

            assertThat(config.getKeys().getHashicorpKeyVaultConfig())
                .isEqualToComparingFieldByField(expectedVaultConfig);
          } catch (Exception ex) {
            throw ex;
          } finally {
            tempTesseraConfig = originalTempTesseraConfig;
          }
        });

    Given(
        "the configfile contains the correct key data",
        () -> {
          createTempTesseraConfig();

          final Config config =
              JaxbUtil.unmarshal(Files.newInputStream(tempTesseraConfig), Config.class);
          // JaxbUtil.marshalWithNoValidation(config, System.out);
          assertThat(config).isNotNull();
        });

    When(
        "^Tessera is started with the following CLI args and (token|approle) environment variables*$",
        (String authMethod, String cliArgs) -> {
          final URL logbackConfigFile = NodeExecManager.class.getResource("/logback-node.xml");
          Path pid = Paths.get(System.getProperty("java.io.tmpdir"), "pidA.pid");

          String formattedArgs =
              String.format(cliArgs, tempTesseraConfig.toString(), pid.toAbsolutePath().toString());

          Path startScript =
              Optional.of("keyvault.hashicorp.dist").map(System::getProperty).map(Paths::get).get();

          final Path distDirectory =
              Optional.of("keyvault.hashicorp.dist")
                  .map(System::getProperty)
                  .map(Paths::get)
                  .get()
                  .resolve("*");

          final List<String> args =
              new ExecArgsBuilder()
                  .withStartScript(startScript)
                  .withClassPathItem(distDirectory)
                  .withArg("--debug")
                  .build();

          args.addAll(Arrays.asList(formattedArgs.split(" ")));

          List<String> jvmArgs = new ArrayList<>();
          jvmArgs.add("-Dspring.profiles.active=disable-unixsocket");
          jvmArgs.add("-Dlogback.configurationFile=" + logbackConfigFile.getFile());
          jvmArgs.add("-Ddebug=true");

          startTessera(args, jvmArgs, tempTesseraConfig, authMethod);
        });

    When(
        "^Tessera is started with the following CLI args, configuration with TSE and (token|approle) environment variables*$",
        (String authMethod, String cliArgs) -> {
          final URL logbackConfigFile = NodeExecManager.class.getResource("/logback-node.xml");
          Path pid = Paths.get(System.getProperty("java.io.tmpdir"), "pidA.pid");

          String formattedArgs =
              String.format(
                  cliArgs, tempTesseraWithTseConfig.toString(), pid.toAbsolutePath().toString());

          Path startScript =
              Optional.of("keyvault.hashicorp.dist").map(System::getProperty).map(Paths::get).get();

          final Path distDirectory =
              Optional.of("keyvault.hashicorp.dist")
                  .map(System::getProperty)
                  .map(Paths::get)
                  .get()
                  .resolve("*");

          final List<String> args =
              new ExecArgsBuilder()
                  .withStartScript(startScript)
                  .withClassPathItem(distDirectory)
                  .withArg("--debug")
                  .build();

          args.addAll(Arrays.asList(formattedArgs.split(" ")));

          List<String> jvmArgs = new ArrayList<>();
          jvmArgs.add("-Dspring.profiles.active=disable-unixsocket");
          jvmArgs.add("-Dlogback.configurationFile=" + logbackConfigFile.getFile());
          jvmArgs.add("-Ddebug=true");

          startTessera(args, jvmArgs, tempTesseraWithTseConfig, authMethod);
        });

    When(
        "^Tessera keygen is run with the following CLI args and (token|approle) environment variables*$",
        (String authMethod, String cliArgs) -> {
          final URL logbackConfigFile = getClass().getResource("/logback-node.xml");

          String formattedArgs =
              String.format(cliArgs, getClientTlsKeystore(), getClientTlsTruststore());

          Path startScript =
              Optional.of("keyvault.hashicorp.dist").map(System::getProperty).map(Paths::get).get();
          final Path distDirectory =
              Optional.of("keyvault.hashicorp.dist")
                  .map(System::getProperty)
                  .map(Paths::get)
                  .get()
                  .resolve("*");

          final List<String> args =
              new ExecArgsBuilder()
                  .withStartScript(startScript)
                  .withClassPathItem(distDirectory)
                  .withArg("--debug")
                  .build();

          args.addAll(Arrays.asList(formattedArgs.split(" ")));

          List<String> jvmArgs = new ArrayList<>();
          jvmArgs.add("-Dspring.profiles.active=disable-unixsocket");
          jvmArgs.add("-Dlogback.configurationFile=" + logbackConfigFile.getFile());
          jvmArgs.add("-Ddebug=true");

          startTessera(args, jvmArgs, null, authMethod);
        });

    Then(
        "Tessera will retrieve the key pair from the vault",
        () -> {
          final URL partyInfoUrl =
              UriBuilder.fromUri("http://localhost").port(8080).path("partyinfo").build().toURL();

          HttpURLConnection partyInfoUrlConnection =
              (HttpURLConnection) partyInfoUrl.openConnection();
          partyInfoUrlConnection.connect();

          int partyInfoResponseCode = partyInfoUrlConnection.getResponseCode();
          assertThat(partyInfoResponseCode).isEqualTo(HttpURLConnection.HTTP_OK);

          JsonReader jsonReader = Json.createReader(partyInfoUrlConnection.getInputStream());

          JsonObject partyInfoObject = jsonReader.readObject();

          assertThat(partyInfoObject).isNotNull();
          assertThat(partyInfoObject.getJsonArray("keys")).hasSize(1);
          assertThat(partyInfoObject.getJsonArray("keys").getJsonObject(0).getString("key"))
              .isEqualTo("/+UuD63zItL1EbjxkKUljMgG8Z1w0AJ8pNOR4iq2yQc=");
        });

    Then(
        "^a new key pair (.+) will have been added to the vault$",
        (String secretName) -> {
          Objects.requireNonNull(vaultToken);

          setKeyStoreProperties();

          final String getPath = String.format("v1/%s/data/%s", secretEngineName, secretName);
          final URL getSecretUrl =
              UriBuilder.fromUri("https://localhost:8200").path(getPath).build().toURL();

          HttpsURLConnection getSecretUrlConnection =
              (HttpsURLConnection) getSecretUrl.openConnection();
          getSecretUrlConnection.setRequestProperty("X-Vault-Token", vaultToken);
          getSecretUrlConnection.connect();

          int getSecretResponseCode = getSecretUrlConnection.getResponseCode();
          assertThat(getSecretResponseCode).isEqualTo(HttpURLConnection.HTTP_OK);

          JsonReader jsonReader = Json.createReader(getSecretUrlConnection.getInputStream());

          JsonObject getSecretObject = jsonReader.readObject();
          JsonObject keyDataObject = getSecretObject.getJsonObject("data").getJsonObject("data");

          assertThat(keyDataObject.size()).isEqualTo(2);
          assertThat(keyDataObject.getString("publicKey")).isNotBlank();
          assertThat(keyDataObject.getString("privateKey")).isNotBlank();
        });

    Given(
        "^PostgeSql server started$",
        () -> {
          hashicorpDbSecretEngineCommands.startPostgreSqlServer();
          hashicorpDbSecretEngineCommands.waitForPostgreSqlServerToBeOnline();
        });

    After(
        () -> {
          if (vaultServerProcess.get() != null && vaultServerProcess.get().isAlive()) {
            vaultServerProcess.get().destroy();
          }

          if (tesseraProcess.get() != null && tesseraProcess.get().isAlive()) {
            tesseraProcess.get().destroy();
          }

          hashicorpDbSecretEngineCommands.stopPostgreSqlServer();
        });
  }

  private void setKeyStoreProperties() {
    System.setProperty("javax.net.ssl.keyStoreType", "jks");
    System.setProperty("javax.net.ssl.keyStore", getClientTlsKeystore());
    System.setProperty("javax.net.ssl.keyStorePassword", "testtest");
    System.setProperty("javax.net.ssl.trustStoreType", "jks");
    System.setProperty("javax.net.ssl.trustStore", getClientTlsTruststore());
    System.setProperty("javax.net.ssl.trustStorePassword", "testtest");
  }

  private void createTempTesseraConfig() {
    if (tempTesseraConfig == null) {
      Map<String, Object> params = new HashMap<>();
      params.put("clientKeystore", getClientTlsKeystore());
      params.put("clientTruststore", getClientTlsTruststore());

      tempTesseraConfig =
          ElUtil.createTempFileFromTemplate(
              getClass().getResource("/vault/tessera-hashicorp-config.json"), params);
      tempTesseraConfig.toFile().deleteOnExit();
    }
  }

  private void createTempTesseraWithPostgreSqlAndSecretEngineConfig() {
    Map<String, Object> params = new HashMap<>();
    params.put("clientKeystore", getClientTlsKeystore());
    params.put("clientTruststore", getClientTlsTruststore());

    tempTesseraConfig =
        ElUtil.createTempFileFromTemplate(
            getClass()
                .getResource(
                    "/vault/tessera-hashicorp-config-postgres-vault-db-secret-engine.json"),
            params);
    tempTesseraConfig.toFile().deleteOnExit();
  }

  private void createTempTesseraConfigWithApprole(String approlePath) {
    if (tempTesseraConfig == null) {
      Map<String, Object> params = new HashMap<>();
      params.put("clientKeystore", getClientTlsKeystore());
      params.put("clientTruststore", getClientTlsTruststore());
      params.put("approlePath", approlePath);

      tempTesseraConfig =
          ElUtil.createTempFileFromTemplate(
              getClass().getResource("/vault/tessera-hashicorp-approle-config.json"), params);
      tempTesseraConfig.toFile().deleteOnExit();
    }
  }

  private void createTempTesseraConfigWithApproleAndWithTSE() {
    if (tempTesseraWithTseConfig == null) {
      Map<String, Object> params = new HashMap<>();
      params.put("clientKeystore", getClientTlsKeystore());
      params.put("clientTruststore", getClientTlsTruststore());

      tempTesseraWithTseConfig =
          ElUtil.createTempFileFromTemplate(
              getClass().getResource("/vault/tessera-hashicorp-approle-with-tse-config.json"),
              params);
      tempTesseraWithTseConfig.toFile().deleteOnExit();
      LOGGER.info("Temporary file with TSE created: {}", tempTesseraWithTseConfig.getFileName());
    }
  }

  private String getTSEEncryptedValue(String value) throws IOException {
    final String mountPath = "v1/transit/encrypt/" + transitKeyName;
    final URL createSecretEngineUrl =
        UriBuilder.fromUri("https://localhost:8200").path(mountPath).build().toURL();
    HttpsURLConnection tseUrlConnection =
        (HttpsURLConnection) createSecretEngineUrl.openConnection();

    tseUrlConnection.setDoOutput(true);
    tseUrlConnection.setRequestMethod("POST");
    tseUrlConnection.setRequestProperty("X-Vault-Token", vaultToken);

    final String encodedBase64Value = Base64.getEncoder().encodeToString(value.getBytes());
    final String createSecretEngineData = "{\"plaintext\": \"" + encodedBase64Value + "\"}";

    try (OutputStreamWriter writer = new OutputStreamWriter(tseUrlConnection.getOutputStream())) {
      writer.write(createSecretEngineData);
    }

    tseUrlConnection.connect();
    assertThat(tseUrlConnection.getResponseCode()).isEqualTo(HttpsURLConnection.HTTP_OK);

    JsonReader jsonReader = Json.createReader(tseUrlConnection.getInputStream());
    JsonObject getTseResponseObject = jsonReader.readObject();
    JsonObject cipherTextObject = getTseResponseObject.getJsonObject("data");
    return cipherTextObject.getString("ciphertext");
  }

  private String getServerTlsCert() {
    return getClass()
        .getResource("/certificates/server-localhost-with-san-ca-chain.cert.pem")
        .getFile();
  }

  private String getServerTlsKey() {
    return getClass().getResource("/certificates/server-localhost-with-san.key.pem").getFile();
  }

  private TestHttpReponse makeHttpRequestAndGetResponse(
      String uri, String path, String method, String data, Map<String, String> headers) {
    try {
      return makeHttpRequestAndGetResponse(
          UriBuilder.fromUri(uri).path(path).build().toURL(), method, data, headers);
    } catch (Exception ex) {
      throw new TestRuntimeException(ex);
    }
  }

  private TestHttpReponse makeHttpRequestAndGetResponse(
      URL url, String method, String data, Map<String, String> headers) {
    try {
      HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();

      urlConnection.setDoOutput(true);
      urlConnection.setRequestMethod(method);
      if (!headers.isEmpty()) {
        headers.forEach(urlConnection::setRequestProperty);
      }

      if (List.of("POST", "PUT").contains(method)) {
        try (OutputStreamWriter writer = new OutputStreamWriter(urlConnection.getOutputStream())) {
          writer.write(data);
        }
      }

      var response = new TestHttpReponse();

      StringBuilder stringBodyContent = new StringBuilder();
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(urlConnection.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          stringBodyContent.append(line);
        }
      }

      response.setResponseCode(urlConnection.getResponseCode());
      response.setResponseBody(stringBodyContent.toString());

      return response;

    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  private String getClientCaTlsCert() {
    return getClass().getResource("/certificates/ca-root.cert.pem").getFile();
  }

  private String getClientTlsKeystore() {
    return getClass().getResource("/certificates/client.jks").getFile();
  }

  private String getClientTlsTruststore() {
    return getClass().getResource("/certificates/truststore.jks").getFile();
  }

  private void startTessera(
      List<String> args, List<String> jvmArgs, Path verifyConfig, String authMethod)
      throws Exception {
    String jvmArgsStr = String.join(" ", jvmArgs);

    LOGGER.info("Starting: {}", String.join(" ", args));
    LOGGER.info("JVM Args: {}", jvmArgsStr);

    ProcessBuilder tesseraProcessBuilder = new ProcessBuilder(args);

    Map<String, String> tesseraEnvironment = tesseraProcessBuilder.environment();
    tesseraEnvironment.put(HASHICORP_CLIENT_KEYSTORE_PWD, "testtest");
    tesseraEnvironment.put(HASHICORP_CLIENT_TRUSTSTORE_PWD, "testtest");
    tesseraEnvironment.put(
        "JAVA_OPTS",
        jvmArgsStr); // JAVA_OPTS is read by start script and is used to provide jvm args

    if ("token".equals(authMethod)) {
      Objects.requireNonNull(vaultToken);
      tesseraEnvironment.put(HASHICORP_TOKEN, vaultToken);
    } else {
      Objects.requireNonNull(approleRoleId);
      Objects.requireNonNull(approleSecretId);
      tesseraEnvironment.put(HASHICORP_ROLE_ID, approleRoleId);
      tesseraEnvironment.put(HASHICORP_SECRET_ID, approleSecretId);
    }

    try {
      tesseraProcess.set(tesseraProcessBuilder.redirectErrorStream(true).start());
    } catch (NullPointerException ex) {
      throw new NullPointerException("Check that application.jar property has been set");
    }

    executorService.submit(
        () -> {
          try (BufferedReader reader =
              Stream.of(tesseraProcess.get().getInputStream())
                  .map(InputStreamReader::new)
                  .map(BufferedReader::new)
                  .findAny()
                  .get()) {

            String line;
            while ((line = reader.readLine()) != null) {
              System.out.println(line);
            }

          } catch (IOException ex) {
            throw new UncheckedIOException(ex);
          }
        });

    CountDownLatch startUpLatch = new CountDownLatch(1);

    if (Objects.nonNull(verifyConfig)) {
      final Config config = JaxbUtil.unmarshal(Files.newInputStream(verifyConfig), Config.class);

      final URL bindingUrl =
          UriBuilder.fromUri(config.getP2PServerConfig().getBindingUri())
              .path("upcheck")
              .build()
              .toURL();

      executorService.submit(
          () -> {
            while (true) {
              try {
                HttpURLConnection conn = (HttpURLConnection) bindingUrl.openConnection();
                conn.connect();

                System.out.println(bindingUrl + " started." + conn.getResponseCode());

                startUpLatch.countDown();
                return;
              } catch (IOException ex) {
                try {
                  TimeUnit.MILLISECONDS.sleep(200L);
                } catch (InterruptedException ex1) {
                }
              }
            }
          });

      boolean started = startUpLatch.await(30, TimeUnit.SECONDS);

      if (!started) {
        System.err.println(bindingUrl + " Not started. ");
      }
    }

    executorService.submit(
        () -> {
          try {
            int exitCode = tesseraProcess.get().waitFor();
            startUpLatch.countDown();
            if (0 != exitCode) {
              System.err.println("Tessera node exited with code " + exitCode);
            }
          } catch (InterruptedException ex) {
            ex.printStackTrace();
          }
        });

    startUpLatch.await(30, TimeUnit.SECONDS);
  }
}
