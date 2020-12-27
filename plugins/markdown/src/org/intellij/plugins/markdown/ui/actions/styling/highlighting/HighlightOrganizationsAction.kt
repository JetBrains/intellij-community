// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.actions.styling.highlighting

import com.intellij.openapi.actionSystem.AnActionEvent

class HighlightOrganizationsAction : HighlightEntitiesAction() {

  override fun isSelected(e: AnActionEvent): Boolean = isHighlightingEnabled

  override fun setSelected(e: AnActionEvent, state: Boolean) = setHighlightingEnabled(key, e, state)

  companion object {
    private const val key = "ner.organizations"
    val isHighlightingEnabled: Boolean
      get() = isHighlightingEnabled(key)
  }
}