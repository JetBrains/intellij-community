// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.actions.styling.highlighting

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.util.FileContentUtilCore

abstract class HighlightEntitiesAction: ToggleAction() {
  companion object {
    @JvmStatic
    protected fun isHighlightingEnabled(key: String) =
      PropertiesComponent.getInstance().getBoolean(makeEnabledKey(key), true)

    @JvmStatic
    protected fun setHighlightingEnabled(key: String, e: AnActionEvent, state: Boolean) {
      PropertiesComponent.getInstance().setValue(makeEnabledKey(key), state, true)

      val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
      FileContentUtilCore.reparseFiles(virtualFile)
    }

    private fun makeEnabledKey(key: String) = "markdown.highlight.$key.enabled"
  }
}