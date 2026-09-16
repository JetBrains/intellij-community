package com.intellij.python.lsp.core.typeEngine

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.registry.Registry
import com.jetbrains.python.PyInternalExecApi
import com.jetbrains.python.sdk.isReadOnly
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.statistics.InterpreterTarget
import com.jetbrains.python.statistics.executionType

object PyTypeEngineUtils {
  /**
   * The registry key that offers the external type engine to a project with more than one module. It
   * is off by default.
   *
   * The key does not decide how many servers run. A tool whose server keeps one workspace for each
   * folder always runs one server for the whole project, see
   * [com.intellij.python.lsp.core.pyLspModulesToServeWith].
   */
  const val MULTI_MODULE_REGISTRY_KEY: String = "pycharm.type.engine.multi.module"

  /** Whether the external type engine may be used on a project with more than one module. */
  val isMultiModuleSupportEnabled: Boolean
    get() = Registry.`is`(MULTI_MODULE_REGISTRY_KEY, false)

  /**
   * Whether the external **type engine** may be used for [project]. The engine is single-module
   * only unless [MULTI_MODULE_REGISTRY_KEY] is set. For the per-module check
   * used by the Pyrefly/ty **tool** (which is always allowed in multi-module projects) see
   * [isLocalNonReadOnlySdk].
   */
  fun isExternalTypeEngineSupported(project: Project): Boolean {
    if (!Registry.`is`("pycharm.type.engine", true))
      return false

    val modules = project.modules
    val multiModuleAllowed = modules.size <= 1 || isMultiModuleSupportEnabled
    // A multi-module project only needs one module the engine can serve: the others are skipped
    // per-module (see PyreflyLspTypeEngineProvider) instead of disabling the engine project-wide.
    return multiModuleAllowed && modules.any { isLocalNonReadOnlySdk(it) }
  }

  /**
   * Whether [module]'s interpreter is a local, non-read-only SDK — the only requirement for running
   * an external LSP tool against it. Unlike [isExternalTypeEngineSupported] this places no
   * single-module restriction, so the Pyrefly/ty tool can run per-module in multi-module projects.
   */
  fun isLocalNonReadOnlySdk(module: Module): Boolean = localNonReadOnlySdk(module) != null

  /**
   * [module]'s interpreter when [isLocalNonReadOnlySdk] accepts it, else `null`. Use this instead of
   * a separate [isLocalNonReadOnlySdk] call and SDK lookup when you need the SDK itself.
   */
  fun localNonReadOnlySdk(module: Module): Sdk? {
    val pythonSdk = module.pythonSdk ?: return null
    @OptIn(PyInternalExecApi::class) // TODO: Do not use executionType, it is for the statistics only
    val supported = !pythonSdk.isReadOnly && pythonSdk.executionType == InterpreterTarget.LOCAL
    return pythonSdk.takeIf { supported }
  }
}
