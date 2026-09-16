// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.frontend

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.python.pytools.common.PyToolId
import com.intellij.python.pytools.common.PyToolConfigurationDto
import com.intellij.python.pytools.common.PyToolDescriptorDto
import com.intellij.python.pytools.frontend.ui.PyToolsUiBundle
import org.jetbrains.annotations.Nls
import javax.swing.Icon

/** Defines the frontend presentation and controls for a Python tool. */
interface PyToolFrontend {
  /** The localized tool name shown in the user interface. */
  val presentableName: @NlsSafe String

  /** The stable identifier shared with the backend tool. */
  val toolId: PyToolId

  /** The tool icon shown in settings, status widgets, and notifications. */
  val icon: Icon

  /** A localized one-line description shown in the External Tools page. */
  val description: @Nls String

  companion object {
    private val EP_NAME: ExtensionPointName<PyToolFrontend> = ExtensionPointName.create("com.intellij.python.pytools.pyToolFrontend")

    /** All registered frontend tool extensions. */
    internal val extensionList: List<PyToolFrontend> get() = EP_NAME.extensionList

    /** Finds a frontend tool by its stable identifier. */
    fun findById(toolId: PyToolId): PyToolFrontend? =
      extensionList.firstOrNull { it.toolId == toolId }
  }
}

/** Marks a tool that appears on the External Tools page and supplies its detail controls. */
interface ExternalPyToolFrontend<C : PyToolConfigurationDto> : PyToolFrontend {
  /** The configuration DTO class that this frontend accepts. */
  val configurationClass: Class<C>

  /** Creates the detail controls that the expanded tool row shows. */
  fun createConfigurable(project: Project, descriptor: PyToolDescriptorDto): UnnamedConfigurable

  /** Returns a short summary of the active features. An empty value hides the summary. */
  fun summary(project: Project, configuration: C): @NlsSafe String = ""

  /**
   * Returns a confirmation message for an enabled-state change.
   *
   * The default asks for confirmation only when the user disables the selected type engine.
   */
  fun enableToggleConfirmation(isOn: Boolean, isTypeEngine: Boolean): @Nls String? {
    if (isOn || !isTypeEngine) return null
    return PyToolsUiBundle.message("py.tool.toggle.confirm.type.engine", presentableName)
  }
}

/** Marks a tool that appears on the Package Managers page. */
interface PackageManagerPyToolFrontend : PyToolFrontend
