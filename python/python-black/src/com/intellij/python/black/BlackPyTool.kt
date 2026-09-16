// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.black

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Version
import com.intellij.openapi.util.registry.Registry
import com.intellij.python.black.configuration.BlackFormatterConfiguration
import com.intellij.python.pytools.backend.PyTool
import com.intellij.python.pytools.backend.PyToolsState
import com.intellij.python.black.common.PyBlackToolConfigurationDto
import com.jetbrains.python.packaging.PyPackageName
import org.jetbrains.annotations.ApiStatus

/**
 * [Black](https://black.readthedocs.io/) — the uncompromising Python code formatter maintained under
 * the PSF. It reformats source into a single, consistent style, leaving little to configure.
 */
@ApiStatus.Internal
class BlackPyTool : PyTool<PyBlackToolConfigurationDto> {
  override val packageName: PyPackageName = PyPackageName.from("black")
  override val minimumSupportedVersion: Version = Version(23, 11, 0)

  @Suppress("DEPRECATION")
  override fun migrateLegacyState(project: Project): PyToolsState.ToolEntry {
    val configuration = BlackFormatterConfiguration.getBlackConfiguration(project)
    val enabled = Registry.`is`("black.formatter.support.enabled") && configuration.enabledOnReformat
    configuration.enabledOnReformat = false
    configuration.executionMode = BlackFormatterConfiguration.ExecutionMode.PACKAGE
    configuration.pathToExecutable = null
    return PyToolsState.ToolEntry(enabled)
  }

  override fun configurationState(project: Project): PyBlackToolConfigurationDto =
    PyBlackToolConfigurationDto(BlackFormatterConfiguration.getBlackConfiguration(project).cmdArguments)

  override fun applyConfigurationState(project: Project, state: PyBlackToolConfigurationDto) {
    BlackFormatterConfiguration.getBlackConfiguration(project).cmdArguments = state.arguments
  }

  companion object {
    fun getInstance(): BlackPyTool = PyTool.EP_NAME.findExtensionOrFail(BlackPyTool::class.java)
  }
}
