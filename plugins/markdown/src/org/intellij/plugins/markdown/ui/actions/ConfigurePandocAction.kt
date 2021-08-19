// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.actions

import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import org.intellij.plugins.markdown.settings.MarkdownSettingsConfigurable

internal class ConfigurePandocAction: AnAction() {
  override fun actionPerformed(event: AnActionEvent) {
    val project = event.getData(CommonDataKeys.PROJECT)
    ShowSettingsUtilImpl.showSettingsDialog(project, MarkdownSettingsConfigurable.ID, "")
  }
}
