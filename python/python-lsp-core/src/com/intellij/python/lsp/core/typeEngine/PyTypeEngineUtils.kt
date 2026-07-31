package com.intellij.python.lsp.core.typeEngine

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.util.registry.Registry
import com.jetbrains.python.sdk.isReadOnly
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.statistics.InterpreterTarget
import com.jetbrains.python.statistics.executionType

object PyTypeEngineUtils {
  /**
   * Whether the external **type engine** may be used for [project]. The engine is single-module
   * only; multi-module support is not implemented yet (PY-89705). For the per-module check used by
   * the Pyrefly/ty **tool** (which is allowed in multi-module projects) see [isLocalNonReadOnlySdk].
   */
  fun isExternalTypeEngineSupported(project: Project): Boolean {
    if (!Registry.`is`("pycharm.type.engine", true))
      return false

    val module = project.modules.singleOrNull() ?: return false
    return isLocalNonReadOnlySdk(module)
  }

  /**
   * Whether [module]'s interpreter is a local, non-read-only SDK — the only requirement for running
   * an external LSP tool against it. Unlike [isExternalTypeEngineSupported] this places no
   * single-module restriction, so the Pyrefly/ty tool can run per-module in multi-module projects.
   */
  fun isLocalNonReadOnlySdk(module: Module): Boolean {
    val pythonSdk = module.pythonSdk ?: return false
    return !pythonSdk.isReadOnly && pythonSdk.executionType == InterpreterTarget.LOCAL
  }
}
