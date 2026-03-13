// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.poetry

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.python.sdk.poetry.getPoetryExecutable
import com.jetbrains.python.sdk.poetry.runPoetry
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

@TestApplication
class PoetryInProjectEnvTest {

  private val projectFixture = projectFixture(openAfterCreation = true)

  private lateinit var moduleBasePath: Path

  @BeforeEach
  fun setUp() {
    moduleBasePath = Path.of(projectFixture.get().basePath!!)
  }

  @Test
  fun `poetry toml is detected in project directory`() {
    moduleBasePath.resolve("poetry.toml").createFile()
    assertThat(moduleBasePath.resolve("poetry.toml").exists()).isTrue()
  }

  @Test
  fun `poetry toml is absent in empty project`() {
    assertThat(moduleBasePath.resolve("poetry.toml").exists()).isFalse()
  }

  @Test
  fun `in-project environment creates venv via env variable`() {
    runBlocking {
      Assumptions.assumeTrue(getPoetryExecutable() != null, "Poetry is not installed")

      runPoetry(moduleBasePath, "init", "-n", "--name", "test-project", inProjectEnv = true).orThrow()
      runPoetry(moduleBasePath, "install", "--no-root", inProjectEnv = true).orThrow()

      assertThat(moduleBasePath.resolve(".venv").isDirectory()).isTrue()
      assertThat(moduleBasePath.resolve("poetry.toml").exists()).isFalse()
    }
  }
}
