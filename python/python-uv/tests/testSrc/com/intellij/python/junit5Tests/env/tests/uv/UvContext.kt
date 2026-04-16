// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.uv

import com.intellij.openapi.application.edtWriteAction
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.junit5Tests.framework.resolvePythonTool
import com.intellij.python.pytools.runtime.PyToolRuntime
import com.intellij.python.uv.backend.runtime.UvConstants
import com.intellij.testFramework.common.timeoutRunBlocking
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.sdk.impl.resolvePythonHome
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.TestInfo
import java.nio.file.Path
import kotlin.io.path.createDirectory

/**
 * Isolated `uv` runtime for tests. Mirrors the structure of `HatchContext`: owns the temporary
 * cache / tool directories, points `uv` at the given Python interpreter, and exposes a
 * [globalRuntime] that tests rebind to a per-test working directory via
 * [PyToolRuntime.withWorkingDirectory].
 */
internal data class UvContext(
  val globalRuntime: PyToolRuntime,
  val uvCacheDirPath: Path,
  val uvToolDirPath: Path,
  val uvToolBinDirPath: Path,
) {
  companion object {
    fun create(pythonPath: PythonBinary, tempDir: Path): UvContext {
      val realTempDir = tempDir.toRealPath()

      lateinit var uvCacheDirPath: Path
      lateinit var uvToolDirPath: Path
      lateinit var uvToolBinDirPath: Path

      timeoutRunBlocking(context = Dispatchers.IO) {
        edtWriteAction {
          uvCacheDirPath = realTempDir.resolve("uv-cache").createDirectory()
          uvToolDirPath = realTempDir.resolve("uv-tool").createDirectory()
          uvToolBinDirPath = realTempDir.resolve("uv-tool-bin").createDirectory()
        }
      }

      val uvExecutablePath = pythonPath.resolvePythonHome().resolvePythonTool("uv")

      val envVars = mapOf(
        UvConstants.ConfigEnvVars.CACHE_DIR to uvCacheDirPath.toString(),
        UvConstants.ConfigEnvVars.TOOL_DIR to uvToolDirPath.toString(),
        UvConstants.ConfigEnvVars.TOOL_BIN_DIR to uvToolBinDirPath.toString(),
        UvConstants.AppEnvVars.PYTHON to pythonPath.toString(),
        UvConstants.AppEnvVars.NO_PROGRESS to "1",
        UvConstants.AppEnvVars.NO_COLOR to "1",
      )

      val runtime = PyToolRuntime(
        binary = BinOnEel(uvExecutablePath, realTempDir),
        execOptions = ExecOptions(env = envVars),
      )

      return UvContext(runtime, uvCacheDirPath, uvToolDirPath, uvToolBinDirPath)
    }
  }
}

private val capitalLetterRegex = "(?<=.)[A-Z]".toRegex()

internal fun TestInfo.projectName(): String {
  val testName = this.displayName.removePrefix("test").substringBefore("(")
  return testName.replace(capitalLetterRegex, "-$0").lowercase()
}
