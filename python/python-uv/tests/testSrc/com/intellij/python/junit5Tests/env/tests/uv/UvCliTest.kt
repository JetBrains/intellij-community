// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.uv

import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.PythonBinaryPath
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pytools.runtime.PyToolRuntime
import com.intellij.python.uv.backend.runtime.uvCli
import com.intellij.testFramework.common.timeoutRunBlocking
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.getOrThrow
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.seconds

@PyEnvTestCase
class UvCliTest {
  private lateinit var myRuntime: PyToolRuntime
  private lateinit var projectRootPath: Path
  private lateinit var projectName: String

  companion object {
    private lateinit var uvContext: UvContext

    @BeforeAll
    @JvmStatic
    fun configureUvRuntime(@PythonBinaryPath pythonPath: PythonBinary, @TempDir tempDir: Path) {
      uvContext = UvContext.create(pythonPath, tempDir)
    }
  }

  @BeforeEach
  fun setUp(testInfo: TestInfo, @TempDir tempDir: Path) {
    val realTempDir = tempDir.toRealPath()
    this.projectName = testInfo.projectName()
    this.projectRootPath = realTempDir.resolve(this.projectName)

    val tempDirUvCli = uvContext.globalRuntime.withWorkingDirectory(realTempDir).getOrThrow().uvCli()
    timeoutRunBlocking { tempDirUvCli.init(projectName) }.getOrThrow()

    this.myRuntime = uvContext.globalRuntime.withWorkingDirectory(projectRootPath).getOrThrow()
    assertTrue(projectRootPath.exists())
    assertTrue(projectRootPath.resolve(PY_PROJECT_TOML).exists())
  }

  @Test
  fun testHelp() = timeoutRunBlocking {
    val output = myRuntime.uvCli().help("version").getOrThrow()
    assertTrue(output.isNotBlank())
  }

  @Test
  fun testGetVersion() = timeoutRunBlocking {
    val version = myRuntime.uvCli().getVersion().getOrThrow()
    assertTrue(version.isNotBlank())
  }

  @Test
  fun testSync() = timeoutRunBlocking(60.seconds) {
    myRuntime.uvCli().sync().getOrThrow()
    assertTrue(projectRootPath.resolve(".venv").exists())
  }

  @Test
  fun testAuth() = timeoutRunBlocking {
    val dir = myRuntime.uvCli().auth().dir().getOrThrow()
    assertTrue(dir.isNotBlank())
  }

  @Test
  fun testCache() = timeoutRunBlocking {
    val cache = myRuntime.uvCli().cache()
    assertTrue(cache.dir().getOrThrow().isNotBlank())
    assertTrue(cache.size().getOrThrow().isNotBlank())
  }

  @Test
  fun testPython(): Unit = timeoutRunBlocking(60.seconds) {
    val python = myRuntime.uvCli().python()
    assertTrue(python.dir().getOrThrow().isNotBlank())
    python.list(onlyInstalled = true).getOrThrow()
  }

  @Test
  fun testSelf() = timeoutRunBlocking {
    val version = myRuntime.uvCli().self().version(short = true).getOrThrow()
    assertTrue(version.isNotBlank())
  }

  @Test
  fun testTool(): Unit = timeoutRunBlocking(60.seconds) {
    val tool = myRuntime.uvCli().tool()
    assertTrue(tool.dir().getOrThrow().isNotBlank())
    tool.list().getOrThrow()
  }
}
