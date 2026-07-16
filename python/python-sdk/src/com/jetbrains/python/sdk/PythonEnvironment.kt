// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.PythonHomePath
import com.jetbrains.python.PythonInfo
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.impl.detectPythonEnvironmentImpl
import com.jetbrains.python.sdk.terminal.Shell
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.pathString

@ApiStatus.Internal
interface HasPythonHome {
  /**
   * Root directory of the environment (venv prefix, conda env prefix, …) — equivalent to `sys.prefix`
   * at runtime. Library/standard-library and `site-packages` directories live underneath this path.
   */
  val pythonHomePath: PythonHomePath
}

@ApiStatus.Internal
interface Activatable {
  data class Script(
    val scriptPath: Path,
    val args: List<String>? = null,
  )

  val activation: (shellType: Shell.Type) -> Script?
}

/**
 * The kind of Python environment detected from the file system layout.
 */
@ApiStatus.Internal
sealed interface PythonEnvironment {
  /**
   * The result of SDK validation: it might be broken, or valid (at least at the moment this class was created).
   * Use `when` to check it, and get [PythonInfo.languageLevel] for Python version.
   */
  val validationInfo: PyResult<PythonInfo>

  /** Absolute path to the Python interpreter executable backing this environment. */
  val pythonBinaryPath: PythonBinary

  /** Virtual environment with parsed `pyvenv.cfg` contents. */
  data class Venv(
    override val validationInfo: PyResult<PythonInfo>,
    override val pythonBinaryPath: PythonBinary,
    override val pythonHomePath: PythonHomePath,
    /**
     * Key/value pairs parsed from `pyvenv.cfg`. Empty for legacy `virtualenv` layouts (Python 2.7-era)
     * that ship `bin/activate_this.py` instead of `pyvenv.cfg`.
     */
    val config: Map<String, String>,
    /** The `lib/` or `lib/pythonX.Y/` directory of the virtual environment. */
    val libRoot: Path,
  ) : PythonEnvironment, HasPythonHome, Activatable {
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
    override val validationInfo: PyResult<PythonInfo>,
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
      val activate = condaExecutable?.resolveSibling("activate")?.takeIf { it.exists() }
                     ?: condaExecutable?.parent?.parent?.resolve("bin/activate")?.takeIf { it.exists() }

      activate?.let {
        Activatable.Script(it, listOf(pythonHomePath.pathString))
      }
    }
  }

  /** System/global Python installation. */
  data class SystemPython(
    override val validationInfo: PyResult<PythonInfo>,
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

/**
 * See [PythonEnvironment.validationInfo]
 */
@get:ApiStatus.Internal
val PythonEnvironment.version: PyResult<LanguageLevel> get() = validationInfo.mapSuccess { it.languageLevel }
