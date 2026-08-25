// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.pyproject

import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.PythonBinaryPath
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.model.internal.pyProjectToml.walkFileSystemNoTomlContent
import com.intellij.python.venv.createVenv
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.util.io.createDirectories
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.venvReader.Directory
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.time.Duration.Companion.minutes

@PyEnvTestCase
internal class PyWalkFileSystemEnvTest {

  @TempDir
  lateinit var tempDir: Directory
  private lateinit var venvDir: Directory
  private lateinit var expectedToml: Path

  @BeforeEach
  fun setUp(@PythonBinaryPath python: PythonBinary): Unit = timeoutRunBlocking(5.minutes) {
    venvDir = tempDir.resolve("some_dir")
    createVenv(python, venvDir.createDirectories()).getOrThrow()
    // A real venv carries no pyproject.toml of its own, so plant one: the walk must not descend into the environment.
    venvDir.resolve(PY_PROJECT_TOML).createFile()
    expectedToml = tempDir.resolve(PY_PROJECT_TOML).createFile()
  }

  @Test
  fun venvExcludedTest(): Unit = timeoutRunBlocking {
    val files = walkFileSystemNoTomlContent(setOf(tempDir)).orThrow().rawTomlFiles
    assertThat("A venv must be pruned from the walk", files, Matchers.contains(expectedToml))
  }
}
