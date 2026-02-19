// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

import com.intellij.execution.RunManager
import com.intellij.openapi.project.Project
import com.intellij.python.pyproject.model.spi.AfterRename
import com.intellij.python.pyproject.model.spi.PyModuleDataTransfer

// Run configurations store module name that can be changed when user changes pyproject.toml, we change them as well
internal class PyRunConfigTransfer : PyModuleDataTransfer {
  override suspend fun beforeRename(project: Project): AfterRename {
    val manager = RunManager.getInstance(project)
    val moduleNameToConfig =
      manager.allConfigurationsList.filterIsInstance<AbstractPythonRunConfiguration<*>>().associateBy { it.moduleName }
    return { oldToNameName ->
      for ((oldModuleName, config) in moduleNameToConfig) {
        val newModuleName = oldToNameName[oldModuleName] ?: continue
        config.moduleName = newModuleName
      }
    }
  }
}