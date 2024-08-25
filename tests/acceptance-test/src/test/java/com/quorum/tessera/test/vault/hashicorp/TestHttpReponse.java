package com.quorum.tessera.test.vault.hashicorp;

public class TestHttpReponse {
  public int getResponseCode() {
    return responseCode;
  }

  public void setResponseCode(int responseCode) {
    this.responseCode = responseCode;
  }

  public String getResponseBody() {
    return responseBody;
  }

  public void setResponseBody(String responseBody) {
    this.responseBody = responseBody;
  }

  private int responseCode;
  private String responseBody;
}
