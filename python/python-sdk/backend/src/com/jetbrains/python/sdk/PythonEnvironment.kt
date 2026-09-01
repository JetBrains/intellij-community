// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.components.service
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.pathSeparator
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.python.sdk.backend.service.ActivatableEnvironmentService
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.PythonHomePath
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.impl.PySdkBundle.message
import com.jetbrains.python.sdk.impl.detectPythonEnvironmentImpl
import com.jetbrains.python.sdk.terminal.Shell
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable
import kotlin.io.path.pathString

@ApiStatus.Internal
interface HasPythonHome {
  /**
   * Root directory of the environment (venv prefix, conda env prefix, …) — equivalent to `sys.prefix`
   * at runtime. Library/standard-library and `site-packages` directories live underneath this path.
   */
  val pythonHomePath: PythonHomePath
}

/**
 * An environment that ships a library directory of its own, as opposed to using the base interpreter's.
 *
 * A virtual environment has one. A conda environment and a system interpreter do not, so a caller that needs a
 * library directory for those must read the interpreter's standard library instead.
 */
@ApiStatus.Internal
interface HasOwnLibRoot {
  /** The environment's own library directory, for example `lib/pythonX.Y/`. */
  val libRoot: Path
}

/**
 * What a terminal must run to activate an environment.
 *
 * The terminal has no knowledge of any kind of environment. It asks the environment for one of these two shapes and
 * hands it to the shell integration.
 */
@ApiStatus.Internal
sealed interface TerminalActivation {
  /** A script the shell sources, with its arguments. */
  data class SourceScript(val scriptPath: Path, val args: List<String>? = null) : TerminalActivation

  /** Shell code the shell runs. conda uses it, because it activates through a hook rather than a script. */
  data class Snippet(val code: String) : TerminalActivation
}

@ApiStatus.Internal
interface Activatable {
  data class Script(
    val scriptPath: Path,
    val args: List<String>? = null,
    /**
     * Post-processes the environment captured after running this activation script. Identity by default.
     * conda uses it to append the base install's `Library\bin` to `PATH` (PY-57146): conda runs base python
     * under the hood and its MKL DLLs live there, which activating a non-base env does not add.
     */
    val postProcessEnv: (Map<String, String>) -> Map<String, String> = { it },
  )

  val activation: (shellType: Shell.Type) -> Script?

  /**
   * What a terminal running [shellType] must do to activate this environment, or null when there is nothing to run.
   *
   * The activation script serves most environments and most shells, so that is the default. An environment overrides
   * this only where a terminal needs something else than the script.
   *
   * [postProcessEnv][Script.postProcessEnv] plays no part here. The shell activates itself and the IDE never reads
   * the result back, which is what makes this different from [activationEnvironment].
   */
  fun terminalActivation(shellType: Shell.Type): TerminalActivation? =
    activation(shellType)?.let { TerminalActivation.SourceScript(it.scriptPath, it.args) }
}

/**
 * A kind of Python environment, detected from the file system layout.
 *
 * The hierarchy is open: a tool contributes its own kind from its own module, together with the
 * [PythonEnvironmentProvider] that detects it. So never match on a concrete kind. Ask the environment instead, through
 * this interface or through [HasPythonHome], [HasOwnLibRoot] and [Activatable].
 */
@ApiStatus.Internal
interface PythonEnvironment {
  /**
   * The interpreter version this environment records about itself, or null when it records none.
   *
   * Read off the layout, never asked of the interpreter: a virtualenv states it in `pyvenv.cfg` and a conda environment
   * in the name of its `conda-meta` entry, so detecting an environment costs no process. A system interpreter and a
   * Python 2.7-era `virtualenv` write nothing down and answer null; whoever needs a version for one of those runs it —
   * see [PythonInterpreter.getVersion].
   *
   * This replaced a `validationInfo` that ran `python --version` on every detection, which is done for every
   * environment a list shows and on every `isValidSdkPath` check.
   */
  val version: @NlsSafe String?

  /** Absolute path to the Python interpreter executable backing this environment. */
  val pythonBinaryPath: PythonBinary

  /** Virtual environment with parsed `pyvenv.cfg` contents. */
  data class Venv(
    override val version: @NlsSafe String?,
    override val pythonBinaryPath: PythonBinary,
    override val pythonHomePath: PythonHomePath,
    /**
     * Key/value pairs parsed from `pyvenv.cfg`. Empty for legacy `virtualenv` layouts (Python 2.7-era)
     * that ship `bin/activate_this.py` instead of `pyvenv.cfg`.
     */
    val config: Map<String, String>,
    /** The `lib/` or `lib/pythonX.Y/` directory of the virtual environment. */
    override val libRoot: Path,
  ) : PythonEnvironment, HasPythonHome, HasOwnLibRoot, Activatable {
    /**
     * Resolves the venv activation script that fits [Shell.Type] in the directory next to the python
     * binary (`Scripts/` on Windows, `bin/` on Unix). Returns `null` if no matching script exists.
     *
     * On Windows the choice depends on the shell: PowerShell needs `Activate.ps1` (cmd's `activate.bat`
     * cannot mutate the calling PowerShell session), while cmd / unknown shells get `activate.bat`.
     */
    override val activation: (shellType: Shell.Type) -> Activatable.Script? = { shellType ->
      val isWindows = pythonBinaryPath.getEelDescriptor().osFamily == EelOsFamily.Windows
      val scriptName = when (shellType) {
        Shell.Type.POWERSHELL -> "Activate.ps1"
        Shell.Type.FISH -> "activate.fish"
        Shell.Type.CSH -> "activate.csh"
        Shell.Type.BASH, Shell.Type.SH, Shell.Type.ZSH, Shell.Type.UNKNOWN ->
          if (isWindows) "activate.bat" else "activate"
      }
      pythonBinaryPath.resolveSibling(scriptName).takeIf { it.exists() }?.let { Activatable.Script(it) }
    }
  }

  /** Conda environment (has `conda-meta` directory). */
  data class Conda(
    override val version: @NlsSafe String?,
    override val pythonBinaryPath: PythonBinary,
    override val pythonHomePath: PythonHomePath,
    /** Path to the environment's `conda-meta/` directory — the marker that identified it as a conda env. */
    val condaMetaPath: Path,
    /** `true` if this is the base conda installation (has `condabin/` or `envs/` subdirectory). */
    val isBase: Boolean,
    /** Path to the `conda` executable resolved relative to this environment, or `null` if not found. */
    val condaExecutable: Path? = null,
  ) : PythonEnvironment, HasPythonHome, Activatable {
    override val activation: (shellType: Shell.Type) -> Activatable.Script? = {
      val osFamily = pythonBinaryPath.getEelDescriptor().osFamily
      val isWindows = osFamily == EelOsFamily.Windows

      val (baseSubdirs, scriptName) = when {
        isWindows -> listOf("Scripts", "condabin") to "activate.bat"
        else -> listOf("bin") to "activate"
      }
      // First existing `activate` script: next to the conda executable, then under the base install.
      val activate = (listOfNotNull(condaExecutable?.resolveSibling(scriptName)) +
                      baseSubdirs.mapNotNull { condaBaseDir?.resolve(it)?.resolve(scriptName) })
        .firstOrNull { it.exists() }

      activate?.let { activateScript ->
        // conda runs base python under the hood; its MKL DLLs live in the base install's `Library\bin`, which
        // activating a (non-base) env does not add. Append it *after* the activated env's own dirs so base python
        // can load them while the env still takes precedence (PY-57146). Windows-only.
        val additionalPaths = listOfNotNull(
          condaBaseDir?.takeIf { isWindows && !isBase }?.resolve("Library")?.resolve("bin")?.takeIf { it.isDirectory() }
        )

        val postProcessEnv: (Map<String, String>) -> Map<String, String> =
          if (additionalPaths.isEmpty()) { env -> env }
          else { env ->
            val patched = LinkedHashMap(env)
            patched.entries.firstOrNull { it.key.equals("PATH", ignoreCase = true) }?.let { (k, v) ->
              patched[k] = (listOf(v) + additionalPaths.map { it.pathString })
                .filter { it.isNotEmpty() }
                .joinToString(osFamily.pathSeparator)
            }
            patched
          }

        Activatable.Script(activateScript, listOf(pythonHomePath.pathString), postProcessEnv)
      }
    }

    /**
     * PowerShell activates through the `conda init` hook, so it gets a snippet. Every other shell sources the script.
     *
     * `conda init` writes the hook into the user profile. The IDE runs the hook by hand, because it cannot ask the
     * user to install the hook and then restart the terminal. When the conda executable is missing, the snippet tells
     * the user to run `conda init` instead.
     */
    override fun terminalActivation(shellType: Shell.Type): TerminalActivation? =
      if (shellType == Shell.Type.POWERSHELL) TerminalActivation.Snippet(powerShellActivationCode())
      else super.terminalActivation(shellType)

    private fun powerShellActivationCode(): String {
      val condaPath = condaExecutable?.takeIf { it.isExecutable() }
      if (condaPath == null) {
        fileLogger().warn("Can't find $condaExecutable, will not activate conda")
        return "echo '${message("powershell.conda.not.activated", "conda")}'"
      }

      // The quotes are inside "Write-Host".
      val errorMessage = message("powershell.conda.not.activated", condaPath).replace('\'', '"')

      // The conda path needs no escape for a space, because conda cannot have one.
      return """
        & '${StringUtil.escapeChar(condaPath.toString(), '\'')}' shell.powershell hook | Out-String | Invoke-Expression ;
        try {
          conda activate '${StringUtil.escapeChar(pythonHomePath.toString(), '\'')}'
        } catch {
          Write-Host('${StringUtil.escapeChar(errorMessage, '\'')}')
        }
        """.trimIndent()
    }

    /** Root of the base conda installation (contains `condabin`/`Scripts`, `Library`, …); the conda executable lives two levels down. */
    private val condaBaseDir: Path? get() = condaExecutable?.parent?.parent
  }

  /** System/global Python installation. */
  data class SystemPython(
    /** Always null: a system interpreter records nothing about itself, so its version is only known by running it. */
    override val version: @NlsSafe String? = null,
    override val pythonBinaryPath: PythonBinary,
  ) : PythonEnvironment
}

/**
 * Detects the Python environment type from the file system layout around this binary.
 *
 * - If `pyvenv.cfg` exists (PEP 405, Python 3.3+), returns [PythonEnvironment.Venv] with the full parsed config map.
 * - If `bin/activate_this.py` or `Scripts/activate_this.py` exists (legacy `virtualenv` for Python 2.7),
 *   returns [PythonEnvironment.Venv] with an empty config map.
 * - If `conda-meta/` directory exists, returns [PythonEnvironment.Conda] with the resolved conda executable.
 * - Otherwise returns [PythonEnvironment.SystemPython].
 *
 * Returns an error if the binary does not exist or is not executable.
 */
@ApiStatus.Internal
@RequiresBackgroundThread
fun PythonBinary.detectPythonEnvironment(): PyResult<PythonEnvironment> = detectPythonEnvironmentImpl()

/** The activation environment for [this] Python environment. */
@ApiStatus.Internal
suspend fun PythonEnvironment.activationEnvironment(): PyResult<Map<String, String>> =
  service<ActivatableEnvironmentService>().activationEnvironment(this)

/** The activation environment for the interpreter at [this] path (its environment is detected on a cache miss). */
@ApiStatus.Internal
suspend fun PythonBinary.activationEnvironment(): PyResult<Map<String, String>> =
  service<ActivatableEnvironmentService>().activationEnvironment(this)
