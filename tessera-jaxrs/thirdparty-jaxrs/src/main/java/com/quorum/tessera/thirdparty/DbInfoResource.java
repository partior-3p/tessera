package com.quorum.tessera.thirdparty;

import com.quorum.tessera.thirdparty.model.DbInfoResponse;
import com.quorum.tessera.transaction.TransactionManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Objects;

@Tag(name = "third-party")
@Path("/dbinfo")
public class DbInfoResource {

  private final TransactionManager transactionManager;

  public DbInfoResource(final TransactionManager transactionManager) {
    this.transactionManager = Objects.requireNonNull(transactionManager);
  }

  @Operation(
      summary = "/dbinfo",
      operationId = "getDbTablesCount",
      description = "get count of different transaction records in db tables")
  @ApiResponse(
      responseCode = "200",
      description = "encrypted transaction and encrypted raw transaction table count",
      content = {
        @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = DbInfoResponse.class))
      })
  @GET
  @Path("/")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getDBInfo() {

    var current = transactionManager.getTransactionCount();

    final JsonObjectBuilder txnCountBuilder = Json.createObjectBuilder();
    current.forEach(txnCountBuilder::add);

    final String output = txnCountBuilder.build().toString();

    return Response.ok().type(MediaType.APPLICATION_JSON).entity(output).build();
  }
}
