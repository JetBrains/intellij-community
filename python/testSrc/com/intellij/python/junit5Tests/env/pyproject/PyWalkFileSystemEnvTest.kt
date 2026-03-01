package com.intellij.python.junit5Tests.env.pyproject

import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.PythonBinaryPath
import com.intellij.python.pyproject.model.internal.pyProjectToml.walkFileSystemNoTomlContent
import com.intellij.python.venv.createVenv
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.util.io.createDirectories
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.venvReader.Directory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.time.Duration.Companion.minutes

@PyEnvTestCase
class PyWalkFileSystemEnvTest {

  @TempDir
  lateinit var tempDir: Directory
  private lateinit var dirToExclude: Directory

  @BeforeEach
  fun setUp(@PythonBinaryPath python: PythonBinary): Unit = timeoutRunBlocking(5.minutes) {
    dirToExclude = tempDir.resolve("some_dir")
    createVenv(python, dirToExclude.createDirectories()).getOrThrow()
  }

  @Test
  fun venvExcludedTest(): Unit = timeoutRunBlocking {
    walkFileSystemNoTomlContent(tempDir)
  }
}
