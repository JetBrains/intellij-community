// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.poetry

import com.intellij.idea.TestFor
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.python.community.impl.poetry.backend.PoetryPyTool
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.pytools.backend.getCustomExecutablePath
import com.intellij.python.pytools.backend.setCustomExecutablePath
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.poetry.sdk.configuration.PyPoetrySdkConfiguration
import com.jetbrains.python.sdk.configuration.CreateInterpreterInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.io.path.readLines
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds

/**
 * A Poetry project with a `pyproject.toml` and no `poetry.lock` has no environment yet. The probe tells this without
 * a run of Poetry. A run of `poetry check --lock` can only fail there, and it gives the same answer.
 *
 * A fake Poetry is pinned as the custom executable. It records its arguments in a log and fails.
 */
@TestApplication
@TestFor(classes = [PyPoetrySdkConfiguration::class], issues = ["PY-93974"])
internal class PyPoetrySdkConfigurationNoLockTest {
  private val modulePathFixture = tempPathFixture(prefix = "poetry-no-lock")
  private val pyProject by projectFixture().pyProjectFixture(modulePathFixture)

  @Test
  fun `a pyproject toml without a poetry lock gives env not found and does not run poetry`(@TempDir fakeDir: Path): Unit =
    timeoutRunBlocking(60.seconds) {
      val log = probe(fakeDir, withLock = false)

      assertThat(log.exists())
        .describedAs("the fake poetry must not run, but it ran with: ${if (log.exists()) log.readLines() else ""}")
        .isFalse()
    }

  @Test
  fun `a pyproject toml with a poetry lock runs poetry`(@TempDir fakeDir: Path): Unit = timeoutRunBlocking(60.seconds) {
    val log = probe(fakeDir, withLock = true)

    assertThat(log.readLines())
      .describedAs("the fake poetry must run, or the other test proves nothing")
      .contains("check --lock")
  }

  /** Asks the Poetry configurator about [pyProject] with the fake Poetry pinned. Returns the log of the fake. */
  private suspend fun probe(fakeDir: Path, withLock: Boolean): Path {
    assumeFalse(SystemInfo.isWindows, "the fake poetry is a POSIX shell script")
    val baseDir = pyProject.baseDir
    val log = fakeDir.resolve("poetry-calls.log")
    val fakePoetry = fakeDir.resolve("poetry")
    withContext(Dispatchers.IO) {
      fakePoetry.writeText("#!/bin/sh\necho \"$*\" >> '${log.pathString}'\nexit 1\n")
      Files.setPosixFilePermissions(fakePoetry, PosixFilePermissions.fromString("rwx------"))
      baseDir.resolve(PY_PROJECT_TOML).writeText("""
        [tool.poetry]
        name = "no-lock"
        version = "0.1.0"
      """.trimIndent())
      if (withLock) baseDir.resolve("poetry.lock").writeText("")
      LocalFileSystem.getInstance().refreshAndFindFileByNioFile(baseDir.resolve(PY_PROJECT_TOML))
    }

    val tool = PoetryPyTool.getInstance()
    val eelDescriptor = baseDir.getEelDescriptor()
    val previous = tool.getCustomExecutablePath(eelDescriptor)
    tool.setCustomExecutablePath(eelDescriptor, fakePoetry)
    try {
      val info = PyPoetrySdkConfiguration().checkEnvironmentAndPrepareSdkCreator(pyProject, emptyList())
      assertThat(info).isInstanceOf(CreateInterpreterInfo.WillCreateEnv::class.java)
    }
    finally {
      tool.setCustomExecutablePath(eelDescriptor, previous)
    }
    return log
  }
}
