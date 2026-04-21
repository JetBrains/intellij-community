// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.ide.IdeBundle
import com.intellij.openapi.options.BoundCompositeSearchableConfigurable
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel

@Deprecated("Configurable definition was moved and it is internal. Please use ShowSettingsUtilImpl.showSettingsDialog(project, TerminalUtil.TERMINAL_CONFIGURABLE_ID, null) to open the Terminal settings page instead of referencing it by class.")
internal class TerminalOptionsConfigurable(private val project: Project) : BoundCompositeSearchableConfigurable<UnnamedConfigurable>(
  displayName = IdeBundle.message("configurable.TerminalOptionsConfigurable.display.name"),
  helpTopic = "reference.settings.terminal",
  _id = TerminalUtil.TERMINAL_CONFIGURABLE_ID
) {
  override fun createConfigurables(): List<UnnamedConfigurable> {
    throw UnsupportedOperationException()
  }

  override fun createPanel(): DialogPanel {
    throw UnsupportedOperationException()
  }
}