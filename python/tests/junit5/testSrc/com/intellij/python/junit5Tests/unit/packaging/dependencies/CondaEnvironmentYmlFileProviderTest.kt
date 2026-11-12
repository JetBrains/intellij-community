// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.packaging.dependencies

import com.intellij.python.community.impl.conda.environmentYml.CondaEnvironmentYmlFile
import com.intellij.python.community.impl.conda.environmentYml.CondaEnvironmentYmlFileProvider
import com.intellij.testFramework.LightVirtualFile
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

internal class CondaEnvironmentYmlFileProviderTest {
  private val provider = CondaEnvironmentYmlFileProvider()

  @ParameterizedTest
  @ValueSource(strings = ["environment.yml", "environment.yaml"])
  fun `recognises both yml and yaml extensions`(name: String) = runBlocking {
    val file = LightVirtualFile(name)
    assertEquals(CondaEnvironmentYmlFile(file), provider.fromFile(file))
  }

  @ParameterizedTest
  @ValueSource(strings = ["env.yml", "environment.yamlx", "environment.txt", "setup.py", "requirements.txt"])
  fun `rejects non-matching names`(name: String) = runBlocking {
    assertNull(provider.fromFile(LightVirtualFile(name)))
  }
}
