// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.black

import com.intellij.codeInsight.actions.onSave.FormatOnSaveOptions
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Version
import com.intellij.openapi.util.registry.Registry
import com.intellij.python.pytools.PyTool
import com.intellij.python.pytools.configuration.ExecutableDiscoveryMode
import com.intellij.python.black.PyBlackBundle.message
import com.intellij.python.black.configuration.BlackFormatterConfigurable
import com.intellij.python.black.configuration.BlackFormatterConfiguration
import com.jetbrains.python.packaging.PyPackageName
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.Path

@ApiStatus.Internal
class BlackPyTool : PyTool {
  override val presentableName: String = "Black"
  override val description: String get() = message("black.tool.description")
  override val packageName: PyPackageName = PyPackageName.from("black")

  /**
   * `--line-ranges` (fragment formatting) requires Black 23.11.0; older versions cannot honour
   * range-restricted formatting requests from the IDE.
   */
  override val minimumSupportedVersion: Version = Version(23, 11, 0)

  override fun legacyEnabled(project: Project): Boolean {
    return Registry.`is`("black.formatter.support.enabled")
           && BlackFormatterConfiguration.getBlackConfiguration(project).enabledOnReformat
  }

  override fun legacyDiscoveryMode(project: Project): ExecutableDiscoveryMode =
    when (BlackFormatterConfiguration.getBlackConfiguration(project).executionMode) {
      BlackFormatterConfiguration.ExecutionMode.BINARY -> ExecutableDiscoveryMode.PATH
      BlackFormatterConfiguration.ExecutionMode.PACKAGE -> ExecutableDiscoveryMode.INTERPRETER
    }

  override fun legacyCustomPath(project: Project): Path? =
    BlackFormatterConfiguration.getBlackConfiguration(project).pathToExecutable
      ?.takeIf { it.isNotBlank() }
      ?.let { Path(it) }

  override val detailConfigurable: (Project) -> UnnamedConfigurable = ::BlackFormatterConfigurable

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
