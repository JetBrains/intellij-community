package com.intellij.selucene

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages

internal class HelloWorldAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    Messages.showInfoMessage(project, "Hello from Selucene!", "Selucene")
  }
}
