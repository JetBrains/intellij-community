// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console.actions

import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil

class ScrollToTheEndAction(private val editor: Editor) : AnAction(ActionsBundle.message("action.EditorConsoleScrollToTheEnd.text"),
                                                                  ActionsBundle.message("action.EditorConsoleScrollToTheEnd.text"),
                                                                  AllIcons.RunConfigurations.Scroll_down) {
  override fun actionPerformed(e: AnActionEvent) {
    EditorUtil.scrollToTheEnd(editor)
  }

  override fun getActionUpdateThread() = ActionUpdateThread.EDT
}