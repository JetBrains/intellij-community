// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.packaging.dependencies

import com.intellij.python.community.impl.conda.environmentYml.CondaEnvironmentYmlSchemaProvider
import com.intellij.testFramework.LightVirtualFile
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

internal class CondaEnvironmentYmlSchemaProviderTest {
  @ParameterizedTest
  @ValueSource(strings = ["environment.yml", "environment.yaml"])
  fun `schema applies to conda env files`(name: String) {
    assertTrue(CondaEnvironmentYmlSchemaProvider.isAvailable(LightVirtualFile(name)))
  }

  @ParameterizedTest
  @ValueSource(strings = ["env.yml", "environments.yaml", "environment.yamlx", "requirements.txt", "setup.py"])
  fun `schema does not apply to other files`(name: String) {
    assertFalse(CondaEnvironmentYmlSchemaProvider.isAvailable(LightVirtualFile(name)))
  }
}
