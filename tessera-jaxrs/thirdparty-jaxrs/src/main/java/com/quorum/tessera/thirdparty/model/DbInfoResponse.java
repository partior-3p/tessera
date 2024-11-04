package com.quorum.tessera.thirdparty.model;

import java.util.Map;

public class DbInfoResponse {

  private Map<String, Long> dbTablesCount;

  public Map<String, Long> getDbTablesCount() {
    return dbTablesCount;
  }

  public void setDbTablesCount(Map<String, Long> dbTablesCount) {
    this.dbTablesCount = dbTablesCount;
  }
}
