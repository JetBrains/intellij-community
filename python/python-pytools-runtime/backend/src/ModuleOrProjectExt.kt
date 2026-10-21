// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.backend.runtime

import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.utils.stderrString
import com.intellij.platform.eel.provider.utils.stdoutString
import com.intellij.python.community.execService.Args
import com.intellij.python.pyproject.model.evolution.findEvoPyProjectIfReady
import com.intellij.python.pyproject.model.evolution.getInterpreter
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.execService.BinaryToExec
import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.ProcessSemiInteractiveFun
import com.intellij.python.community.execService.execute
import com.intellij.python.community.execService.processSemiInteractiveHandler
import com.intellij.python.pytools.backend.PyExecutableCache
import com.intellij.python.pytools.backend.PyTool
import com.intellij.python.pytools.backend.PyToolsBundle
import com.intellij.python.pytools.backend.findExecutableInPath
import com.intellij.python.pytools.backend.getCustomExecutablePath
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.findToolExecutable
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.findPyProject
import com.jetbrains.python.sdk.moduleIfExists
import com.jetbrains.python.sdk.workingDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

/*
 * Running a tool in the context of a module (or bare project) and its Python SDK.
 *
 * The receiver is the module/project because that is what supplies everything the run needs — which interpreter's
 * scripts directory to look in, which Eel machine to talk to, which directory to work in. The tool is a parameter: the
 * same tool runs differently in two modules.
 */

/**
 * uv's tool-runner command. uv contributes it as a secondary executable via [PyTool.executables]; it is looked up by
 * name because the tool entry for uv itself lives in a higher module.
 */
private const val UVX_COMMAND: String = "uvx"

/**
 * How to launch [tool] here: the binary to exec, plus the leading arguments that binary needs.
 *
 * The resolution chain is fixed (discovery mode is no longer selectable): the scripts directory of the interpreter of
 * the Python project, then the user's custom path, then `PATH`. When none of those has it, the tool is run through
 * `uvx`, which needs `--from <package> <executable>` whenever the executable's name differs from its package's
 * (pyright → pyright-langserver).
 *
 * Waits for the first snapshot to find the interpreter. A caller in a read action calls
 * [toolExecutableWithBaseArgsIfReady] instead, so it does not wait under the lock.
 */
suspend fun ModuleOrProject.toolExecutableWithBaseArgs(
  tool: PyTool,
  executableName: String = tool.packageName.name,
  workingDir: Path? = null,
): PyResult<Pair<BinaryToExec, List<String>>> =
  toolExecutableWithBaseArgs(tool, executableName, workingDir, findPyProject()?.getInterpreter())

/**
 * [toolExecutableWithBaseArgs] that reads the interpreter from the snapshot and does not wait for it. Before the first
 * snapshot, the lookup skips the interpreter and goes on with the custom path.
 */
suspend fun ModuleOrProject.toolExecutableWithBaseArgsIfReady(
  tool: PyTool,
  executableName: String = tool.packageName.name,
  workingDir: Path? = null,
): PyResult<Pair<BinaryToExec, List<String>>> =
  toolExecutableWithBaseArgs(tool, executableName, workingDir, moduleIfExists?.findEvoPyProjectIfReady()?.interpreter)

private suspend fun ModuleOrProject.toolExecutableWithBaseArgs(
  tool: PyTool,
  executableName: String,
  workingDir: Path?,
  interpreter: PythonInterpreter?,
): PyResult<Pair<BinaryToExec, List<String>>> {
  val eelDescriptor = project.getEelDescriptor()
  val eelApi = eelDescriptor.toEelApi()
  val customPath = tool.getCustomExecutablePath(eelDescriptor)

  val toolBinaryPath = interpreter?.findToolExecutable(tool, executableName)
                       ?: customPath
                       ?: findExecutableInPath(eelApi, executableName)

  val workDir = (workingDir ?: workingDirectory)?.asEelPath()

  if (toolBinaryPath != null) {
    return PyResult.success(BinOnEel(toolBinaryPath, workDir = workDir) to emptyList())
  }
  // uvx (installed with uv) may live in a per-user dir off PATH, so detect it like any other tool.
  val uvxPath = PyTool.findExecutable(UVX_COMMAND)?.let { PyExecutableCache.getInstance().get(eelDescriptor, it) }
                ?: return PyResult.localizedError(PyToolsBundle.message("uvx.is.not.installed"))
  val uvxArgs = if (executableName == tool.packageName.name) listOf(executableName)
  else listOf("--from", tool.packageName.name, executableName)
  return PyResult.success(BinOnEel(uvxPath, workDir = workDir) to uvxArgs)
}

/** Runs [tool] here with [args], returning its stdout, or its stderr as the failure. */
suspend fun ModuleOrProject.executeTool(
  tool: PyTool,
  args: Args,
  execOptions: ExecOptions = ExecOptions(),
): PyResult<String> = withContext(Dispatchers.IO) {
  val (binToExec, baseArgs) = toolExecutableWithBaseArgs(tool).getOr { return@withContext it }
  ExecService().execute(
    binary = binToExec,
    args = Args(*baseArgs.toTypedArray()).add(args),
    options = execOptions,
  ) { processOutput ->
    when (processOutput.exitCode) {
      0 -> Result.success(processOutput.stdoutString)
      else -> Result.failure(processOutput.stderrString)
    }
  }
}

/** Runs [tool] here, handing the live process to [processSemiInteractiveFun] instead of collecting its output. */
suspend fun <T> ModuleOrProject.executeToolInteractive(
  tool: PyTool,
  args: Args,
  workingDir: Path? = null,
  execOptions: ExecOptions = ExecOptions(),
  processSemiInteractiveFun: ProcessSemiInteractiveFun<T>,
): PyResult<T> = withContext(Dispatchers.IO) {
  val (binToExec, baseArgs) = toolExecutableWithBaseArgs(tool, workingDir = workingDir).getOr { return@withContext it }
  ExecService().executeAdvanced(
    binary = binToExec,
    args = Args(*baseArgs.toTypedArray()).add(args),
    options = execOptions,
    processInteractiveHandler = processSemiInteractiveHandler(code = processSemiInteractiveFun),
  )
}
