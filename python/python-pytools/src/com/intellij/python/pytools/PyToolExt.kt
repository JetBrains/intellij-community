package com.intellij.python.pytools

import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelOsFamily
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
import com.intellij.python.pytools.PyToolsBundle.message
import com.intellij.python.pytools.impl.detectExecutableOnEel
import com.intellij.python.pytools.services.PyCustomExecutablePaths
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.PythonInterpreter
import com.jetbrains.python.sdk.baseDir
import com.jetbrains.python.sdk.moduleIfExists
import com.jetbrains.python.sdk.pythonInterpreter
import com.jetbrains.python.sdk.pythonSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.isExecutable

/**
 * uv's tool-runner command. uv contributes it as a secondary [PyExecutable] via [PyTool.executables];
 * pytools resolves it by name (it lives in a higher module) with [PyTool.findExecutable].
 */
private const val UVX_COMMAND: String = "uvx"

fun PyTool.getState(project: Project): PyToolsState.ToolEntry = PyToolsState.getInstance(project).getEntry(this)

fun PyTool.isEnabledOn(project: Project): Boolean = getState(project).enabled

/**
 * A tool is "active" when the user enabled it as an LSP tool, or it is the project's selected type
 * engine. Server start/stop and LSP feature gating key off this (rather than the raw enabled flag)
 * so that a tool acting as the type engine keeps its shared LSP server running and its features on,
 * even though its External Tools enable toggle is locked. See [PyTool.isSelectedAsTypeEngine].
 */
fun PyTool.isActiveOn(project: Project): Boolean = isEnabledOn(project) || isSelectedAsTypeEngine(project)

/**
 * The user-chosen custom executable path for this executable on [eelDescriptor]'s machine, or `null`
 * for auto-detection. Stored per Eel machine in [PyCustomExecutablePaths] (machine-wide, not per project).
 */
fun PyExecutable.getCustomExecutablePath(eelDescriptor: EelDescriptor): Path? =
  PyCustomExecutablePaths.getInstance().get(eelDescriptor, this)

/** Persist (or clear, when `null`) the custom executable path for this executable on [eelDescriptor]'s machine. */
fun PyExecutable.setCustomExecutablePath(eelDescriptor: EelDescriptor, path: Path?): Unit =
  PyCustomExecutablePaths.getInstance().set(eelDescriptor, this, path)

suspend fun PyTool.getExecutableWithBaseArgs(
  moduleOrProject: ModuleOrProject,
  executableName: String = packageName.name,
  workingDir: Path? = null,
): PyResult<Pair<BinaryToExec, List<String>>> {
  val eelDescriptor = moduleOrProject.project.getEelDescriptor()
  val eelApi = eelDescriptor.toEelApi()
  val customPath = getCustomExecutablePath(eelDescriptor)

  // Fixed resolution chain (the discovery mode is no longer selectable): the
  // interpreter's scripts dir, then a custom path, then PATH; if still unresolved, run via uvx below.
  val pyRichSdk = moduleOrProject.moduleIfExists?.pythonSdk?.pythonInterpreter()
  val toolBinaryPath = pyRichSdk?.let { findExecutableInSdk(it, executableName) }
                       ?: customPath ?: findExecutableInPath(eelApi, executableName)

  val workDir = workingDir
                ?: moduleOrProject.moduleIfExists?.baseDir?.toNioPath()
                ?: moduleOrProject.project.baseDir?.toNioPath()

  return if (toolBinaryPath != null) {
    BinOnEel(toolBinaryPath, workDir = workDir).let { PyResult.success(it to emptyList()) }
  }
  else {
    // uvx (installed with uv) may live in a per-user dir off PATH, so detect it like any other tool.
    val uvxPath = PyTool.findExecutable(UVX_COMMAND)?.let { PyExecutableCache.getInstance().get(eelDescriptor, it) }
                  ?: return PyResult.localizedError(message("uvx.is.not.installed"))

    // `uvx <pkg>` only works when the package's entry point matches its name. When the executable
    // differs (e.g. pyright → pyright-langserver) uvx needs `--from <pkg> <executable>`.
    val uvxArgs = if (executableName == packageName.name) listOf(executableName)
                  else listOf("--from", packageName.name, executableName)
    BinOnEel(uvxPath, workDir = workDir).let { PyResult.success(it to uvxArgs) }
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
fun PyTool.findExecutableInSdk(pythonInterpreter: PythonInterpreter, executableName: String = packageName.name): Path? {
  return pythonInterpreter.pythonBinaryPath?.let { basePythonBinaryPath ->
    val osFamily = basePythonBinaryPath.getEelDescriptor().osFamily
    basePythonBinaryPath.resolveSibling(osFamily.getOsSpecificBinaryName(executableName)).takeIf { it.isExecutable() }
  }
}

/**
 * Resolve [executableName] in the environment [eelApi] describes: on `PATH` and in the well-known per-user
 * install directories tool installers use (pip's user scripts dir, uv/pipx's `~/.local/bin`, …). Detection
 * goes through [detectExecutableOnEel] so it matches how the executable was installed — a plain `PATH`
 * lookup misses those per-user dirs, which are frequently not on `PATH` on Windows (PY-91493). Not tied
 * to a [PyTool]: also used to find `uv`/`uvx`, which have no tool entry.
 */
suspend fun findExecutableInPath(eelApi: EelApi, executableName: String): Path? =
  detectExecutableOnEel(eelApi, pyExecutableSpec(executableName))

/**
 * Installs this tool's executable into the environment described by [eel] via the tool's [PyTool.manager]
 * (by default a `uv tool install` / pip install; conda uses its own). Returns the resolved executable
 * path, or an error when the tool has no installer ([PyTool.manager] is `null`).
 */
suspend fun PyTool.performToolInstallation(eel: EelApi): PyResult<Path> =
  manager?.install(this, eel) ?: PyResult.localizedError(message("python.tool.install.no.installer", presentableName))

/**
 * Upgrades this tool to the latest version in the environment described by [eel] via the tool's
 * [PyTool.manager]. Returns the resolved executable path, or an error when the tool has no installer.
 */
suspend fun PyTool.performToolUpgrade(eel: EelApi): PyResult<Path> =
  manager?.upgrade(this, eel) ?: PyResult.localizedError(message("python.tool.install.no.installer", presentableName))
