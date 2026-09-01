// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.components.service
import com.intellij.python.sdk.backend.service.ActivatableEnvironmentService
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.PythonHomePath
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.impl.detectPythonEnvironmentImpl
import com.jetbrains.python.sdk.terminal.Shell
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

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

  /** System/global Python installation. */
  data class SystemPython(
    /** Always null: a system interpreter records nothing about itself, so its version is only known by running it. */
    override val version: @NlsSafe String? = null,
    override val pythonBinaryPath: PythonBinary,
  ) : PythonEnvironment
}

/**
 * Detects the Python environment from the file system layout around this binary.
 *
 * Each kind is detected by its own [PythonEnvironmentProvider], so this function names no kind. A binary that no
 * other provider claims is a [PythonEnvironment.SystemPython].
 *
 * Returns an error if the binary does not exist or is not executable, or if a provider owns the layout but the
 * layout is broken.
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
