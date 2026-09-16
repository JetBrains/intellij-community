// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.black.frontend

import com.intellij.codeInsight.actions.onSave.FormatOnSaveOptions
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.python.black.common.PyBlackToolConfigurationDto
import com.intellij.python.pytools.common.PyToolId
import com.intellij.python.pytools.common.PyToolDescriptorDto
import com.intellij.python.pytools.frontend.ExternalPyToolFrontend
import com.intellij.python.black.common.icons.PythonBlackCommonIcons
import com.intellij.python.black.frontend.PyBlackFrontendBundle.message
import javax.swing.Icon

internal class BlackPyToolFrontend : ExternalPyToolFrontend<PyBlackToolConfigurationDto> {
  override val presentableName: String = "Black"
  override val description: String get() = message("black.tool.description")
  override val toolId: PyToolId = BLACK_TOOL_ID
  override val icon: Icon get() = PythonBlackCommonIcons.Black
  override val configurationClass: Class<PyBlackToolConfigurationDto> = PyBlackToolConfigurationDto::class.java
  override fun createConfigurable(project: Project, descriptor: PyToolDescriptorDto): UnnamedConfigurable {
    return BlackFormatterConfigurable(project, descriptor)
  }

  override fun summary(project: Project, configuration: PyBlackToolConfigurationDto): String {
    val arguments = configuration.arguments
    return buildList {
      add(message("black.enable.black.checkbox.label"))
      if (FormatOnSaveOptions.getInstance(project).isRunOnSaveEnabled) add(message("black.enable.action.on.save.label"))
      arguments.trim().takeIf { it.isNotEmpty() }?.let { add(it) }
    }.joinToString(", ")
  }
}

internal val BLACK_TOOL_ID: PyToolId = PyToolId("black")
