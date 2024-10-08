package com.quorum.tessera.test.vault.hashicorp;

import io.cucumber.junit.Cucumber;
import io.cucumber.junit.CucumberOptions;
import org.junit.runner.RunWith;

@RunWith(Cucumber.class)
@CucumberOptions(
    features = {
      "build/resources/test/features/vault/hashicorp.feature",
      "build/resources/test/features/vault/hashicorp-db-secret-engine.feature",
    },
    plugin = {"pretty"})
public class RunHashicorpIT {}
