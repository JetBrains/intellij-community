// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core.frontend

import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.python.lsp.core.common.PyLspToolConfigurationDto
import com.intellij.python.pytools.common.PyToolDescriptorDto
import com.intellij.python.pytools.frontend.ExternalPyToolFrontend
import org.jetbrains.annotations.Nls

/** Defines the common controls and feature summary for an LSP tool. */
interface LspPyToolFrontend : ExternalPyToolFrontend<PyLspToolConfigurationDto> {
  override val configurationClass: Class<PyLspToolConfigurationDto>
    get() = PyLspToolConfigurationDto::class.java

  /** Creates the standard LSP feature controls. */
  override fun createConfigurable(project: Project, descriptor: PyToolDescriptorDto): UnnamedConfigurable =
    createLspToolConfigurable(project, this)

  /** Returns the enabled LSP features from the backend configuration. */
  override fun summary(project: Project, configuration: PyLspToolConfigurationDto): String =
    pyLspToolFeaturesSummary(configuration, this)

  /** The localized formatting label, or null when the tool does not support formatting. */
  val formattingLabel: @Nls String? get() = null

  /** The localized import sorting label, or null when the tool does not support import sorting. */
  val sortImportsLabel: @Nls String? get() = null
}
