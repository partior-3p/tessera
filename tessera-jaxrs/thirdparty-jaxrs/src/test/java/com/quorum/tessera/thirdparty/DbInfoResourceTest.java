package com.quorum.tessera.thirdparty;

import static org.mockito.Mockito.*;

import com.quorum.tessera.transaction.TransactionManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DbInfoResourceTest {

  private TransactionManager transactionManager;

  private DbInfoResource dbInfoResource;

  @Before
  public void onSetup() {
    this.transactionManager = mock(TransactionManager.class);

    this.dbInfoResource = new DbInfoResource(transactionManager);
  }

  @After
  public void onTearDown() {
    verifyNoMoreInteractions(transactionManager);
  }

  @Test
  public void getDbInfo() {}
}
