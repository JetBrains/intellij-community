package com.intellij.python.pytools

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.provider.utils.stderrString
import com.intellij.platform.eel.provider.utils.stdoutString
import com.intellij.platform.eel.where
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.execService.BinaryToExec
import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.ProcessSemiInteractiveFun
import com.intellij.python.community.execService.execute
import com.intellij.python.community.execService.processSemiInteractiveHandler
import com.intellij.python.pytools.PyToolsBundle.message
import com.intellij.python.pytools.configuration.ExecutableDiscoveryMode
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.PyRichSdk
import com.jetbrains.python.sdk.baseDir
import com.jetbrains.python.sdk.moduleIfExists
import com.jetbrains.python.sdk.pyRichSdk
import com.jetbrains.python.sdk.pythonSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.isExecutable

fun PyTool.getState(project: Project): PyToolsState.ToolEntry = PyToolsState.getInstance(project).getEntry(this)

fun PyTool.isEnabledOn(project: Project): Boolean = getState(project).enabled

suspend fun PyTool.getExecutableWithBaseArgs(
  moduleOrProject: ModuleOrProject,
  executableName: String = packageName.name,
  workingDir: Path? = null,
): PyResult<Pair<BinaryToExec, List<String>>> {
  val state = getState(moduleOrProject.project)

  val toolBinaryPath = when (state.discoveryMode) {
    ExecutableDiscoveryMode.INTERPRETER -> {
      val pyRichSdk = moduleOrProject.moduleIfExists?.pythonSdk?.pyRichSdk()
      pyRichSdk?.let { findExecutableInSdk(it, executableName) } ?: findExecutableInPath(state, executableName)
    }
    ExecutableDiscoveryMode.PATH -> findExecutableInPath(state, executableName)
    ExecutableDiscoveryMode.UVX -> null
  }

  val workDir = workingDir
                ?: moduleOrProject.moduleIfExists?.baseDir?.toNioPath()
                ?: moduleOrProject.project.baseDir?.toNioPath()

  return if (toolBinaryPath != null) {
    BinOnEel(toolBinaryPath, workDir = workDir).let { PyResult.success(it to emptyList()) }
  }
  else {
    val uvxPath = localEel.exec.where("uvx")
                  ?: return PyResult.localizedError(message("uvx.is.not.installed"))

    // `uvx <pkg>` only works when the package's entry point matches its name. When the executable
    // differs (e.g. pyright → pyright-langserver) uvx needs `--from <pkg> <executable>`.
    val uvxArgs = if (executableName == packageName.name) listOf(executableName)
                  else listOf("--from", packageName.name, executableName)
    BinOnEel(uvxPath.asNioPath(), workDir = workDir).let { PyResult.success(it to uvxArgs) }
  }
}

suspend fun PyTool.executeOn(
  moduleOrProject: ModuleOrProject,
  args: Args,
  execOptions: ExecOptions = ExecOptions(),
): PyResult<String> = withContext(Dispatchers.IO) {
  val (binToExec, baseArgs) = getExecutableWithBaseArgs(moduleOrProject).getOr { return@withContext it }
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


suspend fun <T> PyTool.executeInteractiveOn(
  moduleOrProject: ModuleOrProject,
  args: Args,
  workingDir: Path? = null,
  execOptions: ExecOptions = ExecOptions(),
  processSemiInteractiveFun: ProcessSemiInteractiveFun<T>,
): PyResult<T> = withContext(Dispatchers.IO) {
  val (binToExec, baseArgs) = getExecutableWithBaseArgs(moduleOrProject, workingDir = workingDir).getOr { return@withContext it }
  val execService = ExecService()
  execService.executeAdvanced(
    binary = binToExec,
    args = Args(*baseArgs.toTypedArray()).add(args),
    options = execOptions,
    processInteractiveHandler = processSemiInteractiveHandler(code = processSemiInteractiveFun)
  )
}


suspend fun PyTool.resolveVersion(moduleOrProject: ModuleOrProject): PyResult<Version> {
  val versionOutput = executeOn(moduleOrProject, Args("--version")).getOr { return it }
  return versionOutput.parseVersion(packageName.name)
}

private fun EelOsFamily.getOsSpecificBinaryName(binaryName: String): String = when (this) {
  EelOsFamily.Posix -> binaryName
  EelOsFamily.Windows -> "$binaryName.exe"
}

/**
 * only local sdks are supported currently
 */
fun PyTool.findExecutableInSdk(pyRichSdk: PyRichSdk, executableName: String = packageName.name): Path? {
  return pyRichSdk.pythonBinaryPath?.let { basePythonBinaryPath ->
    val osFamily = basePythonBinaryPath.getEelDescriptor().osFamily
    basePythonBinaryPath.resolveSibling(osFamily.getOsSpecificBinaryName(executableName)).takeIf { it.isExecutable() }
  }
}

private fun PyTool.findExecutableInPath(state: PyToolsState.ToolEntry, executableName: String = packageName.name): Path? {
  return state.customToolBinaryPath ?: findExecutableInPath(executableName)
}

fun PyTool.findExecutableInPath(
  executableName: String = packageName.name,
  osFamily: EelOsFamily = LocalEelDescriptor.osFamily,
): Path? = PathEnvironmentVariableUtil.findInPath(osFamily.getOsSpecificBinaryName(executableName))?.toPath()
