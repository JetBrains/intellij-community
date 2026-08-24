// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.utils.stderrString
import com.intellij.platform.eel.provider.utils.stdoutString
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.execService.BinaryToExec
import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.ProcessSemiInteractiveFun
import com.intellij.python.community.execService.execute
import com.intellij.python.community.execService.processSemiInteractiveHandler
import com.intellij.python.pytools.PyExecutableCache
import com.intellij.python.pytools.PyTool
import com.intellij.python.pytools.PyToolsBundle
import com.intellij.python.pytools.Version
import com.intellij.python.pytools.findExecutableInPath
import com.intellij.python.pytools.getCustomExecutablePath
import com.intellij.python.pytools.parseVersion
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.baseDir
import com.jetbrains.python.sdk.moduleIfExists
import com.jetbrains.python.sdk.pythonInterpreter
import com.jetbrains.python.sdk.pythonSdk
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
 * The resolution chain is fixed (discovery mode is no longer selectable): the module interpreter's scripts directory,
 * then the user's custom path, then `PATH`. When none of those has it, the tool is run through `uvx`, which needs
 * `--from <package> <executable>` whenever the executable's name differs from its package's (pyright →
 * pyright-langserver).
 */
suspend fun ModuleOrProject.toolExecutableWithBaseArgs(
  tool: PyTool,
  executableName: String = tool.packageName.name,
  workingDir: Path? = null,
): PyResult<Pair<BinaryToExec, List<String>>> {
  val eelDescriptor = project.getEelDescriptor()
  val eelApi = eelDescriptor.toEelApi()
  val customPath = tool.getCustomExecutablePath(eelDescriptor)

  val interpreter = moduleIfExists?.pythonSdk?.pythonInterpreter()
  val toolBinaryPath = interpreter?.findToolExecutable(tool, executableName)
                       ?: customPath
                       ?: findExecutableInPath(eelApi, executableName)

  val workDir = workingDir
                ?: moduleIfExists?.baseDir?.toNioPath()
                ?: project.baseDir?.toNioPath()

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

/** [tool]'s own reported version, as run here — `<tool> --version`, parsed. */
suspend fun ModuleOrProject.resolveToolVersion(tool: PyTool): PyResult<Version> {
  val versionOutput = executeTool(tool, Args("--version")).getOr { return it }
  return versionOutput.parseVersion(tool.packageName.name)
}
