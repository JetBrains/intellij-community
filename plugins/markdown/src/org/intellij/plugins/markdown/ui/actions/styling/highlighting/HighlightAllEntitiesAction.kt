// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.actions.styling.highlighting

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import org.intellij.plugins.markdown.highlighting.ner.HighlightedEntityType

class HighlightAllEntitiesAction : ToggleAction() {
  override fun isSelected(e: AnActionEvent): Boolean {
    val project = e.project ?: return false
    return HighlightedEntityType.values().all { it.isEnabled(project) }
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val allSelected = isSelected(e)
    for (entity in HighlightedEntityType.values()) {
      entity.highlightingManager.setHighlightingEnabled(!allSelected, e)
    }
  }
}