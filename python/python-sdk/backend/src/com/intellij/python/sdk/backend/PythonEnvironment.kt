// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.openapi.util.NlsSafe
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.PythonHomePath
import com.jetbrains.python.sdk.ShellType
import java.nio.file.Path

/**
 * What a live shell runs to activate an environment.
 *
 * The caller has no knowledge of any kind of environment. It asks the environment for one of these two shapes and
 * hands it to the shell integration.
 */
sealed interface ShellActivation {
  /** A script the shell sources, with its arguments. */
  data class SourceScript(
    val scriptPath: Path,
    val args: List<String>? = null,
    /**
     * Post-processes the environment captured after running this activation script. Identity by default.
     * conda uses it to append the base install's `Library\bin` to `PATH` (PY-57146): conda runs base python
     * under the hood and its MKL DLLs live there, which activating a non-base env does not add.
     */
    val postProcessEnv: (Map<String, String>) -> Map<String, String> = { it },
  ) : ShellActivation

  // TODO: Only PowerShell runs a snippet. Extend the terminal API to run any code.
  //  A file is not an option: PowerShell requires a signed script.
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

  /**
   * Whether this environment belongs to one project, so its SDK must record which one.
   *
   * False for an environment that many projects share: a system installation, or a base conda install. Such an
   * environment records nothing about a project and must not be asked for one, so that is the default. True for an
   * environment a project owns, where a missing project is a broken configuration rather than a legal state.
   *
   * @see com.intellij.python.sdk.backend.validate
   */
  val requiresAssociation: Boolean get() = false

  /** Whether anything must run to activate this environment. Answers for every shell, unlike [activationScript]. */
  val isActivatable: Boolean get() = false

  /**
   * What a shell of [shellType] must run to activate this environment, or null when there is nothing to run.
   *
   * Two callers use it:
   * - The terminal gives the result to the shell the user sees.
   * - The IDE sources a [ShellActivation.SourceScript] in a child shell and keeps the variables that the script
   *   added. Only this caller applies [ShellActivation.SourceScript.postProcessEnv].
   *
   * When [shellType] is null, the shell is not known. Then the environment uses the default shell of the OS:
   * cmd on Windows, sh on Unix. This is usually correct, but not always.
   *
   * Most environments return a [ShellActivation.SourceScript]. conda on PowerShell returns a [ShellActivation.Snippet],
   * because it activates through the `conda init` hook.
   */
  @RequiresBackgroundThread
  fun activationScript(shellType: ShellType?): ShellActivation?
}

/**
 * A system-wide Python installation: no root of its own, no library of its own, nothing to activate, and no project of
 * its own. Every project on the machine may use it, so it keeps the default [requiresAssociation] of `false`.
 */
data class SystemPythonEnvironment(
  /** Always null: a system interpreter records nothing about itself, so its version is only known by running it. */
  override val version: @NlsSafe String? = null,
  override val pythonBinaryPath: PythonBinary,
) : PythonEnvironment {
  override fun activationScript(shellType: ShellType?): ShellActivation? = null
}

