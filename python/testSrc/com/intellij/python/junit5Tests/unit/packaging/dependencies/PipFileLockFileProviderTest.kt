// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.packaging.dependencies

import com.intellij.testFramework.LightVirtualFile
import com.jetbrains.python.sdk.pipenv.PipfileFile
import com.jetbrains.python.sdk.pipenv.PipfileFileProvider
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

internal class PipFileLockFileProviderTest {
  private val provider = PipfileFileProvider()

  @Test
  fun `recognises Pipfile_lock`() = runBlocking {
    val file = LightVirtualFile("Pipfile.lock")
    assertEquals(PipfileFile(file), provider.fromFile(file))
  }

  @ParameterizedTest
  @ValueSource(strings = ["Pipfile", "pipfile.lock", "Pipfile.lock.bak", "requirements.txt", "setup.py", "pyproject.toml"])
  fun `rejects non-matching names`(name: String) = runBlocking {
    assertNull(provider.fromFile(LightVirtualFile(name)))
  }
}
