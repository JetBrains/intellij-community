// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.packaging.dependencies

import com.intellij.testFramework.LightVirtualFile
import com.jetbrains.python.requirements.SetupPyFile
import com.jetbrains.python.requirements.impl.SetupPyFileProvider
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

internal class SetupPyFileProviderTest {
  private val provider = SetupPyFileProvider()

  @Test
  fun `recognises setup_py`() = runBlocking {
    val file = LightVirtualFile("setup.py")
    assertEquals(SetupPyFile(file), provider.fromFile(file))
  }

  @ParameterizedTest
  @ValueSource(strings = ["setup.cfg", "Setup.py", "setup.pyc", "requirements.txt", "pyproject.toml", "README.md"])
  fun `rejects non-matching names`(name: String) = runBlocking {
    assertNull(provider.fromFile(LightVirtualFile(name)))
  }
}
