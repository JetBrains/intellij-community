// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.packaging.dependencies

import com.intellij.testFramework.LightVirtualFile
import com.jetbrains.python.requirements.RequirementsTxtFile
import com.jetbrains.python.requirements.impl.RequirementsTxtFileProvider
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

internal class RequirementsTxtFileProviderTest {
  private val provider = RequirementsTxtFileProvider()

  @ParameterizedTest
  @ValueSource(strings = ["requirements.txt", "requirements-dev.txt", "requirements.in", "requirements-prod.in"])
  fun `recognises requirements files by name`(name: String) = runBlocking {
    val file = LightVirtualFile(name)
    assertEquals(RequirementsTxtFile(file), provider.fromFile(file))
  }

  @Test
  fun `recognises files under a requirements directory regardless of name`() = runBlocking {
    val file = object : LightVirtualFile("base.txt") {
      override fun getPath(): String = "/project/requirements/$name"
    }
    assertEquals(RequirementsTxtFile(file), provider.fromFile(file))
  }

  @ParameterizedTest
  @ValueSource(strings = ["setup.py", "pyproject.toml", "Pipfile.lock", "environment.yml", "README.md", "requirements.md"])
  fun `rejects non-matching files`(name: String) = runBlocking {
    assertNull(provider.fromFile(LightVirtualFile(name)))
  }
}
