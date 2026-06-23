// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.black

import com.intellij.codeInsight.actions.onSave.FormatOnSaveOptions
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Version
import com.intellij.openapi.util.registry.Registry
import com.intellij.python.pytools.PyTool
import com.intellij.python.pytools.PyToolsState
import com.intellij.python.pytools.configuration.ExecutableDiscoveryMode
import com.intellij.python.pytools.ui.PyToolDetailConfigurableProvider
import com.intellij.python.black.PyBlackBundle.message
import com.intellij.python.black.configuration.BlackFormatterConfigurable
import com.intellij.python.black.configuration.BlackFormatterConfiguration
import com.jetbrains.python.packaging.PyPackageName
import org.jetbrains.annotations.ApiStatus
import kotlin.io.path.Path

@ApiStatus.Internal
class BlackPyTool : PyTool, PyToolDetailConfigurableProvider {
  override val presentableName: String = "Black"
  override val description: String get() = message("black.tool.description")
  override val packageName: PyPackageName = PyPackageName.from("black")

  /**
   * `--line-ranges` (fragment formatting) requires Black 23.11.0; older versions cannot honour
   * range-restricted formatting requests from the IDE.
   */
  override val minimumSupportedVersion: Version = Version(23, 11, 0)

  @Suppress("DEPRECATION")
  override fun migrateLegacyState(project: Project): PyToolsState.ToolEntry {
    val cfg = BlackFormatterConfiguration.getBlackConfiguration(project)
    val entry = PyToolsState.ToolEntry(
      enabled = Registry.`is`("black.formatter.support.enabled") && cfg.enabledOnReformat,
      discoveryMode = when (cfg.executionMode) {
        BlackFormatterConfiguration.ExecutionMode.BINARY -> ExecutableDiscoveryMode.PATH
        BlackFormatterConfiguration.ExecutionMode.PACKAGE -> ExecutableDiscoveryMode.INTERPRETER
      },
      customToolBinaryPath = cfg.pathToExecutable?.takeIf { it.isNotBlank() }?.let { Path(it) },
    )
    cfg.enabledOnReformat = false
    cfg.executionMode = BlackFormatterConfiguration.ExecutionMode.PACKAGE
    cfg.pathToExecutable = null
    return entry
  }

  override fun createConfigurable(project: Project): UnnamedConfigurable = BlackFormatterConfigurable(project)

  override fun summaryFor(project: Project): String {
    val cfg = BlackFormatterConfiguration.getBlackConfiguration(project)
    return buildList {
      add(message("black.enable.black.checkbox.label"))
      if (FormatOnSaveOptions.getInstance(project).isRunOnSaveEnabled) {
        add(message("black.enable.action.on.save.label"))
      }
      cfg.cmdArguments.trim().takeIf { it.isNotEmpty() }?.let { add(it) }
    }.joinToString(", ")
  }

  @Suppress("CompanionObjectInExtension")
  companion object {
    fun getInstance(): BlackPyTool = PyTool.EP_NAME.findExtensionOrFail(BlackPyTool::class.java)
  }
}
