// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.openapi.util.NlsSafe
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.PythonHomePath
import com.jetbrains.python.sdk.terminal.Shell
import java.nio.file.Path

/**
 * A script a shell sources to activate an environment.
 */
data class ActivationScript(
  val scriptPath: Path,
  val args: List<String>? = null,
  /**
   * Post-processes the environment captured after running this activation script. Identity by default.
   * conda uses it to append the base install's `Library\bin` to `PATH` (PY-57146): conda runs base python
   * under the hood and its MKL DLLs live there, which activating a non-base env does not add.
   */
  val postProcessEnv: (Map<String, String>) -> Map<String, String> = { it },
)

/**
 * What a live shell runs to activate an environment.
 *
 * The caller has no knowledge of any kind of environment. It asks the environment for one of these two shapes and
 * hands it to the shell integration.
 */
sealed interface ShellActivation {
  /** A script the shell sources, with its arguments. */
  data class SourceScript(val scriptPath: Path, val args: List<String>? = null) : ShellActivation

  /** Shell code the shell runs. conda uses it, because it activates through a hook rather than a script. */
  data class Snippet(val code: String) : ShellActivation
}

/**
 * A kind of Python environment, detected from the file system layout.
 *
 * The hierarchy is open: a tool contributes its own kind from its own module, together with the
 * [PythonEnvironmentProvider] that detects it. So never match on a concrete kind. Ask the environment instead.
 *
 * Every question below is meaningful for any environment, and null is a real answer: a system interpreter has no
 * root of its own, no library directory of its own, and nothing to activate. A question that only one tool can
 * answer does not belong here. It belongs on that tool's own type, where only that tool's module can read it.
 */
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

  /**
   * Root directory of the environment (venv prefix, conda env prefix, …) — equivalent to `sys.prefix` at runtime.
   * Library and `site-packages` directories live underneath it.
   *
   * Null for an interpreter that is not in an environment of its own.
   */
  val pythonHomePath: PythonHomePath? get() = null

  /**
   * The environment's own library directory, for example `lib/pythonX.Y/`.
   *
   * Null when the environment uses the base interpreter's library, which a conda environment and a system
   * interpreter do. A caller that needs a library directory for those reads the interpreter's standard library.
   */
  val libRoot: Path? get() = null

  /** Whether anything must run to activate this environment. Answers for every shell, unlike the two below. */
  val isActivatable: Boolean get() = false

  /**
   * The script the IDE sources to read this environment's variables, or null when there is none.
   *
   * The IDE runs it in a child shell and keeps the variables it added, so the script must be a file that a shell
   * can source. Use [shellActivation] for the shell the user sees.
   */
  fun activationScript(shellType: Shell.Type): ActivationScript? = null

  /**
   * What a live shell running [shellType] must run to activate this environment, or null when there is nothing to
   * run.
   *
   * Sourcing [activationScript] serves most environments and most shells, so that is the default. The two answers
   * differ only where a shell activates through something the IDE cannot source and capture: conda on PowerShell
   * runs the `conda init` hook, and hands the IDE its `activate` script instead.
   *
   * [postProcessEnv][ActivationScript.postProcessEnv] plays no part here. The shell activates itself and the IDE
   * never reads the result back, which is what makes this different from [activationEnvironment].
   */
  fun shellActivation(shellType: Shell.Type): ShellActivation? =
    activationScript(shellType)?.let { ShellActivation.SourceScript(it.scriptPath, it.args) }

}

/**
 * A system-wide Python installation: no root of its own, no library of its own, nothing to activate.
 */
data class SystemPythonEnvironment(
  /** Always null: a system interpreter records nothing about itself, so its version is only known by running it. */
  override val version: @NlsSafe String? = null,
  override val pythonBinaryPath: PythonBinary,
) : PythonEnvironment

