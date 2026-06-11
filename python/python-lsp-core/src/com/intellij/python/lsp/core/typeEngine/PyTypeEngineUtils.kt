package com.intellij.python.lsp.core.typeEngine

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.util.registry.Registry
import com.jetbrains.python.sdk.isReadOnly
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.statistics.InterpreterTarget
import com.jetbrains.python.statistics.executionType

object PyTypeEngineUtils {
  fun isExternalTypeEngineSupported(project: Project): Boolean {
    if (!Registry.`is`("pycharm.type.engine", true))
      return false

    val module = project.modules.singleOrNull() ?: return false
    val pythonSdk = module.pythonSdk ?: return false

    return !pythonSdk.isReadOnly && pythonSdk.executionType == InterpreterTarget.LOCAL
  }
}
