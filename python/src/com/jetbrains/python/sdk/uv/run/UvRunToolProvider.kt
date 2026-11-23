// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv.run

import com.intellij.python.pytools.resolveExecutable
import com.jetbrains.python.run.features.PyInterpreterRunToolProvider
import com.intellij.python.sdk.backend.binaryPathString
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.pytools.common.FusId
import com.intellij.python.uv.backend.UvPyTool
import com.jetbrains.python.PyBundle
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.run.features.PyRunToolData
import com.jetbrains.python.run.features.PyRunToolId
import com.jetbrains.python.run.features.PyRunToolParameters
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.uv.impl.createUvCli
import com.jetbrains.python.sdk.uv.impl.createUvLowLevel
import java.nio.file.Path

/**
 * PyRunToolProvider implementation that runs scripts/modules using `uv run`.
 *
 * Matches every interpreter whose environment uv manages, on the IDE machine and on a target.
 */
internal class UvRunToolProvider : PyInterpreterRunToolProvider() {
  override val manager: FusId get() = UvPyTool.getInstance().fusId

  override suspend fun <P : PathHolder> getRunToolParameters(
    interpreter: PythonInterpreter,
    fileSystem: FileSystem<P>,
    inlineScriptTarget: Path?,
  ): PyRunToolParameters {
    val uvPath = requireNotNull(UvPyTool.getInstance().resolveExecutable(fileSystem)) {
      "Unable to find uv executable."
    }
    if (inlineScriptTarget != null) {
      return scriptRunToolParameters(uvPath, fileSystem, inlineScriptTarget)
    }

    val pythonPath = fileSystem.parsePath(interpreter.binaryPathString).getOrThrow()
    val venvPath = fileSystem.resolvePythonHome(pythonPath).toStringOnRemoteMachine()
    return PyRunToolParameters(uvPath, listOf("run"), prepareEnv(venvPath))
  }

  /**
   * Runs a PEP 723 script by its own environment's interpreter rather than by the SDK's.
   *
   * `uv run --script` would have uv pick the interpreter itself, leaving nowhere to put interpreter options or the
   * debugger's wrapper. Syncing first yields an interpreter that takes both, and pointing `VIRTUAL_ENV` at the script
   * environment keeps uv from resolving the surrounding project on top of it.
   */
  private suspend fun <P : PathHolder> scriptRunToolParameters(
    uvPath: P,
    fileSystem: FileSystem<P>,
    scriptPath: Path,
  ): PyRunToolParameters {
    val uvCli = createUvCli(uvPath, fileSystem)
    val uv = createUvLowLevel(scriptPath.parent, uvCli)
    val environment = uv.syncScript(scriptPath).getOrThrow()

    return PyRunToolParameters(
      uvPath,
      // The interpreter goes in the arguments because the SDK's own is dropped below.
      listOf("run", environment.pythonPath),
      mapOf(),
      includeOriginalExe = false,
    )
  }

  private fun prepareEnv(venvPath: String) = mapOf("VIRTUAL_ENV" to venvPath, "UV_PROJECT_ENVIRONMENT" to venvPath)

  override val runToolData: PyRunToolData = PyRunToolData(
    PyRunToolId("uv.run"),
    PyBundle.message("uv.run.configuration.type.display.name"),
    PyBundle.message("python.run.configuration.fragments.python.group"),
    label = PyBundle.message("uv.run.tool.label"),
  )

  override val initialToolState: Boolean = true
}
