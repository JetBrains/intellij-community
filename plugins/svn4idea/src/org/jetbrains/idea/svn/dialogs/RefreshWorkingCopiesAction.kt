// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager

private fun AnActionEvent.getWorkingCopiesPanel(): CopiesPanel? =
  project?.let { ChangesViewContentManager.getInstance(it) }?.getActiveComponent(CopiesPanel::class.java)

class RefreshWorkingCopiesAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val panel = e.getWorkingCopiesPanel()

    e.presentation.apply {
      isVisible = panel != null
      isEnabled = panel?.isRefreshing == false
    }
  }

  override fun actionPerformed(e: AnActionEvent) = e.getWorkingCopiesPanel()!!.refresh()
}