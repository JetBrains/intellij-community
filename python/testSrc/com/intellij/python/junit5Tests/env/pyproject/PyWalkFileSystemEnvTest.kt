// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.pyproject

import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.PythonBinaryPath
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.model.internal.platformBridge.refreshProjectRootsIntoVfs
import com.intellij.python.pyproject.model.internal.pyProjectToml.findPyProjectTomlFilesInIndex
import com.intellij.python.venv.createVenv
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.io.createDirectories
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.venvReader.Directory
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.time.Duration.Companion.minutes

/**
 * Checks that the search skips a real virtualenv.
 *
 * The other rules of the search have cheap unit coverage in `PyIndexSearchTest`, which fakes a virtualenv with
 * an empty `bin/python`. Only this test builds a genuine environment with a real interpreter.
 */
@PyEnvTestCase
internal class PyWalkFileSystemEnvTest {

  private val pathFixture = tempPathFixture()
  private val projectFixture = projectFixture(pathFixture)

  private lateinit var tempDir: Directory
  private lateinit var venvDir: Directory
  private lateinit var expectedToml: Path

  @BeforeEach
  fun setUp(@PythonBinaryPath python: PythonBinary): Unit = timeoutRunBlocking(5.minutes) {
    tempDir = pathFixture.get()
    venvDir = tempDir.resolve("some_dir")
    createVenv(python, venvDir.createDirectories()).getOrThrow()
    // A real venv carries no pyproject.toml of its own, so plant one: the search must not descend into the environment.
    venvDir.resolve(PY_PROJECT_TOML).createFile()
    expectedToml = tempDir.resolve(PY_PROJECT_TOML).createFile()
  }

  @Test
  fun venvExcludedTest(): Unit = timeoutRunBlocking {
    // The search reads the VFS, and a test writes with `java.nio`, so the tree needs a load first.
    refreshProjectRootsIntoVfs(projectFixture.get())
    val files = findPyProjectTomlFilesInIndex(setOf(tempDir), excludedPaths = emptySet())
    assertThat("A venv must be skipped by the search", files, Matchers.contains(expectedToml))
  }
}
