package com.intellij.python.sdkConfigurator.backend.impl.platformBridge

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.python.sdkConfigurator.backend.impl.ModuleConfigurationMode
import com.intellij.python.sdkConfigurator.backend.impl.configureSdkBg

internal class ConfigureSDKAction : AnAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    configureSdkBg(project, ModuleConfigurationMode.INTERACTIVE)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}


