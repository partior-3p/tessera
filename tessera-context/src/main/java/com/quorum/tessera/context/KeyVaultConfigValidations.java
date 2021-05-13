package com.quorum.tessera.context;

import com.quorum.tessera.config.KeyConfiguration;
import com.quorum.tessera.config.keypairs.ConfigKeyPair;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import javax.validation.ConstraintViolation;

public interface KeyVaultConfigValidations {

  static KeyVaultConfigValidations create() {
    return ServiceLoader.load(KeyVaultConfigValidations.class).findFirst().get();
  }

  Set<ConstraintViolation<?>> validate(KeyConfiguration keys, List<ConfigKeyPair> configKeyPairs);
}
