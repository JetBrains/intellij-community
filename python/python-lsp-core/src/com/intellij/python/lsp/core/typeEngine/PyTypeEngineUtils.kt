package com.intellij.python.lsp.core.typeEngine

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.registry.Registry
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.python.sdk.backend.NativePythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.TargetPythonInterpreter
import com.jetbrains.python.PyInternalExecApi
import com.jetbrains.python.sdk.isReadOnly
import com.jetbrains.python.statistics.InterpreterTarget
import com.jetbrains.python.statistics.executionType

object PyTypeEngineUtils {
  /**
   * The registry key that offers the external type engine to a project with more than one module. It
   * is off by default.
   *
   * The key does not decide how many servers run. A tool whose server keeps one workspace for each
   * folder always runs one server for the whole project, see
   * [com.intellij.python.lsp.core.pyLspProjectsToServeWith].
   */
  const val MULTI_MODULE_REGISTRY_KEY: String = "pycharm.type.engine.multi.module"

  /** Whether the external type engine may be used on a project with more than one module. */
  val isMultiModuleSupportEnabled: Boolean
    get() = Registry.`is`(MULTI_MODULE_REGISTRY_KEY, false)

  /**
   * Whether the external **type engine** may be used for [project]. The engine is single-module
   * only unless [MULTI_MODULE_REGISTRY_KEY] is set. For the per-module check
   * used by the Pyrefly/ty **tool** (which is always allowed in multi-module projects) see
   * [isServedInterpreter].
   */
  fun isExternalTypeEngineSupported(project: Project): Boolean {
    // A multi-module project only needs one Python project the engine can serve: the others are skipped
    // per-project (see PyreflyLspTypeEngineProvider) instead of disabling the engine project-wide. This
    // runs under a read lock, so it reads the snapshot and does not wait. Before the first snapshot the
    // engine is not supported, and the first snapshot updates the servers.
    val snapshot = EvoPyProjectModel.getInstance(project).snapshotOrNull() ?: return false
    return isExternalTypeEngineSupported(project, snapshot)
  }

  /** [isExternalTypeEngineSupported] for [snapshot]. */
  fun isExternalTypeEngineSupported(project: Project, snapshot: EvoPyProjectModel.Snapshot): Boolean {
    if (!Registry.`is`("pycharm.type.engine", true))
      return false
    val multiModuleAllowed = project.modules.size <= 1 || isMultiModuleSupportEnabled
    return multiModuleAllowed && snapshot.interpreters.any(::isServedInterpreter)
  }

  /**
   * [interpreter] when an LSP tool can run against it, else `null`: a native interpreter that is not read-only. A tool
   * runs on the machine of the interpreter through Eel, so WSL and Docker count. A legacy target, such as SSH, has no
   * such machine.
   */
  fun servedInterpreterOf(interpreter: PythonInterpreter): NativePythonInterpreter? = when (interpreter) {
    is NativePythonInterpreter -> interpreter.takeUnless { it.isReadOnly }
    is TargetPythonInterpreter -> null
  }

  /** Whether an LSP tool can run against [interpreter], see [servedInterpreterOf]. */
  fun isServedInterpreter(interpreter: PythonInterpreter): Boolean = servedInterpreterOf(interpreter) != null

  /**
   * Whether [sdk] runs on this machine. A read-only system interpreter counts, so use this for a tool
   * that only reads the environment.
   */
  fun isLocalSdk(sdk: Sdk): Boolean {
    @OptIn(PyInternalExecApi::class) // TODO: Do not use executionType, it is for the statistics only
    return sdk.executionType == InterpreterTarget.LOCAL
  }
}
