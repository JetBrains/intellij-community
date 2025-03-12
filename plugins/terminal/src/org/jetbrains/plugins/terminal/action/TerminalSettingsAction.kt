// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableWithId
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.TERMINAL_CONFIGURABLE_ID
import java.util.function.Predicate

@ApiStatus.Internal
class TerminalSettingsAction : DumbAwareAction(IdeBundle.message("action.text.settings"), null, AllIcons.General.Settings) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    // Match the Terminal configurable by ID.
    // Can't use matching by configurable class name because actual configurable can be wrapped in case of Remote Dev.
    ShowSettingsUtil.getInstance().showSettingsDialog(project, Predicate { configurable: Configurable ->
      configurable is ConfigurableWithId && configurable.getId() == TERMINAL_CONFIGURABLE_ID
    }, null)
  }
}