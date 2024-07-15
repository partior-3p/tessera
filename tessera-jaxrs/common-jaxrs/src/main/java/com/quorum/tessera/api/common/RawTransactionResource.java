package com.quorum.tessera.api.common;

import static com.quorum.tessera.version.MultiTenancyVersion.MIME_TYPE_JSON_2_1;
import static jakarta.ws.rs.core.MediaType.*;

import com.quorum.tessera.api.StoreRawRequest;
import com.quorum.tessera.api.StoreRawResponse;
import com.quorum.tessera.config.constraints.ValidBase64;
import com.quorum.tessera.data.MessageHash;
import com.quorum.tessera.enclave.PrivacyMode;
import com.quorum.tessera.encryption.PublicKey;
import com.quorum.tessera.transaction.TransactionManager;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.tags.Tags;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Provides endpoints for dealing with raw transactions */
@Tags({@Tag(name = "quorum-to-tessera"), @Tag(name = "third-party")})
@Path("/")
public class RawTransactionResource {

  private static final Logger LOGGER = LoggerFactory.getLogger(RawTransactionResource.class);

  public static final String ENDPOINT_STORE_RAW = "storeraw";

  private final TransactionManager transactionManager;

  private final Base64.Decoder base64Decoder = Base64.getDecoder();

  private final Base64.Encoder base64Encoder = Base64.getEncoder();

  public RawTransactionResource() {
    this(TransactionManager.create());
  }

  public RawTransactionResource(final TransactionManager transactionManager) {
    this.transactionManager = Objects.requireNonNull(transactionManager);
  }

  // hide this operation from swagger generation; the /storeraw operation is overloaded and must be
  // documented in a single place
  @Hidden
  @POST
  @Path(ENDPOINT_STORE_RAW)
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  public Response store(
      @RequestBody(
              required = true,
              content = @Content(schema = @Schema(implementation = StoreRawRequest.class)))
          @NotNull
          @Valid
          final StoreRawRequest request) {
    final StoreRawResponse storeRawResponse = this.forwardRequest(request);
    return Response.ok().type(APPLICATION_JSON).entity(storeRawResponse).build();
  }

  // path /storeraw is overloaded (application/json and application/vnd.tessera-2.1+json); swagger
  // annotations cannot handle situations like this so this operation documents both
  @Operation(
      summary = "/storeraw",
      operationId = "encryptAndStoreVersion",
      description = "encrypts a payload and stores result in the \"raw\" database",
      requestBody =
          @RequestBody(
              required = true,
              content = {
                @Content(
                    mediaType = APPLICATION_JSON,
                    schema = @Schema(implementation = StoreRawRequest.class)),
                @Content(
                    mediaType = MIME_TYPE_JSON_2_1,
                    schema = @Schema(implementation = StoreRawRequest.class))
              }))
  @ApiResponse(
      responseCode = "200",
      description = "hash of encrypted payload",
      content = {
        @Content(
            mediaType = APPLICATION_JSON,
            schema = @Schema(implementation = StoreRawResponse.class)),
        @Content(
            mediaType = MIME_TYPE_JSON_2_1,
            schema = @Schema(implementation = StoreRawResponse.class))
      })
  @ApiResponse(responseCode = "404", description = "'from' key in request body not found")
  @POST
  @Path(ENDPOINT_STORE_RAW)
  @Consumes(MIME_TYPE_JSON_2_1)
  @Produces(MIME_TYPE_JSON_2_1)
  public Response storeVersion21(@NotNull @Valid final StoreRawRequest request) {
    final StoreRawResponse storeRawResponse = this.forwardRequest(request);
    return Response.ok().type(MIME_TYPE_JSON_2_1).entity(storeRawResponse).build();
  }

  private StoreRawResponse forwardRequest(final StoreRawRequest request) {
    final PublicKey sender =
        request.getFrom().map(PublicKey::from).orElseGet(transactionManager::defaultPublicKey);

    final com.quorum.tessera.transaction.StoreRawRequest storeRawRequest =
        com.quorum.tessera.transaction.StoreRawRequest.Builder.create()
            .withSender(sender)
            .withPayload(request.getPayload())
            .build();

    final com.quorum.tessera.transaction.StoreRawResponse response =
        transactionManager.store(storeRawRequest);

    final StoreRawResponse storeRawResponse = new StoreRawResponse();
    storeRawResponse.setKey(response.getHash().getHashBytes());

    return storeRawResponse;
  }

  @POST
  @Path("privateTransfer")
  @Consumes(APPLICATION_OCTET_STREAM)
  @Produces(TEXT_PLAIN)
  public Response privateTransfer(
    @HeaderParam("c11n-from")
    @Parameter(
      description =
        "public key identifying the server's key pair that will be used in the encryption; if not set, default used",
      schema = @Schema(format = "base64"))
    @Valid
    @ValidBase64
    final String sender,
    @HeaderParam("c11n-to")
    @Parameter(
      description = "comma-separated list of recipient public keys",
      schema = @Schema(format = "base64"))
    final String recipientKeys,
    @Schema(description = "data to be encrypted") @NotNull @Size(min = 1) @Valid
    final byte[] payload) {

    final PublicKey senderKey =
      Optional.ofNullable(sender)
        .filter(Predicate.not(String::isEmpty))
        .map(base64Decoder::decode)
        .map(PublicKey::from)
        .orElseGet(transactionManager::defaultPublicKey);

    final List<PublicKey> recipients =
      Stream.of(recipientKeys)
        .filter(Objects::nonNull)
        .filter(s -> !Objects.equals("", s))
        .map(v -> v.split(","))
        .flatMap(Arrays::stream)
        .map(base64Decoder::decode)
        .map(PublicKey::from)
        .collect(Collectors.toList());

    final com.quorum.tessera.transaction.SendRequest request =
      com.quorum.tessera.transaction.SendRequest.Builder.create()
        .withSender(senderKey)
        .withRecipients(recipients)
        .withPayload(payload)
        .withPrivacyMode(PrivacyMode.STANDARD_PRIVATE)
        .withAffectedContractTransactions(Collections.emptySet())
        .withExecHash(new byte[0])
        .build();

    final com.quorum.tessera.transaction.SendResponse sendResponse =
      transactionManager.send(request);

    final String encodedTransactionHash =
      Optional.of(sendResponse)
        .map(com.quorum.tessera.transaction.SendResponse::getTransactionHash)
        .map(MessageHash::getHashBytes)
        .map(base64Encoder::encodeToString)
        .get();

    LOGGER.debug("Encoded key: {}", encodedTransactionHash);

    URI location =
      UriBuilder.fromPath("transaction")
        .path(URLEncoder.encode(encodedTransactionHash, StandardCharsets.UTF_8))
        .build();

    return Response.status(Response.Status.OK)
      .entity(encodedTransactionHash)
      .location(location)
      .build();
  }

  @GET
  @Path("receivePrivateTransfer")
  @Consumes(APPLICATION_OCTET_STREAM)
  @Produces(APPLICATION_OCTET_STREAM)
  public Response receivePrivateTransfer(
    @Schema(
      description = "hash indicating encrypted payload to retrieve from private transfers",
      format = "base64")
    @ValidBase64
    @NotNull
    @HeaderParam(value = "c11n-key")
    String hash,
    @Schema(
      description =
        "(optional) public key of recipient of the encrypted payload; used in decryption; if not provided, decryption is attempted with all known recipient keys in turn",
      format = "base64")
    @ValidBase64
    @HeaderParam(value = "c11n-to")
    String recipientKey) {

    LOGGER.debug("Received receivePrivateTransfer request for hash : {}, recipientKey: {}", hash, recipientKey);

    MessageHash transactionHash =
      Optional.of(hash).map(base64Decoder::decode).map(MessageHash::new).get();
    PublicKey recipient =
      Optional.ofNullable(recipientKey)
        .map(base64Decoder::decode)
        .map(PublicKey::from)
        .orElse(null);
    com.quorum.tessera.transaction.ReceiveRequest request =
      com.quorum.tessera.transaction.ReceiveRequest.Builder.create()
        .withTransactionHash(transactionHash)
        .withRecipient(recipient)
        .build();

    com.quorum.tessera.transaction.ReceiveResponse receiveResponse =
      transactionManager.receive(request);

    byte[] payload = receiveResponse.getUnencryptedTransactionData();

    return Response.status(Response.Status.OK).entity(payload).build();
  }
}
