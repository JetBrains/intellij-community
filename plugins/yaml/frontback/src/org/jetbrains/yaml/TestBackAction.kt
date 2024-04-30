package org.jetbrains.yaml

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class TestBackAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    println("HELLO ACTION!!!!")
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = true
  }
}