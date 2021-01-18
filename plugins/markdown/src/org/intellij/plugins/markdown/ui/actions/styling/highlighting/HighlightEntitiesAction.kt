// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.actions.styling.highlighting

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.Project
import com.intellij.util.FileContentUtilCore

abstract class HighlightEntitiesAction(private val highlightingManager: EntityHighlightingManager): ToggleAction() {
  override fun isSelected(e: AnActionEvent): Boolean {
    val project = e.project ?: return false
    return highlightingManager.isHighlightingEnabled(project)
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) = highlightingManager.setHighlightingEnabled(state, e)
}

abstract class EntityHighlightingManager(private val key: String) {
  private fun makeEnabledKey(key: String) = "markdown.highlight.$key.enabled"

  fun isHighlightingEnabled(project: Project) = PropertiesComponent.getInstance(project).getBoolean(makeEnabledKey(key), true)

  fun setHighlightingEnabled(value: Boolean, e: AnActionEvent) {
    val project = e.project ?: return
    PropertiesComponent.getInstance(project).setValue(makeEnabledKey(key), value, true)

    val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    FileContentUtilCore.reparseFiles(virtualFile)
  }
}