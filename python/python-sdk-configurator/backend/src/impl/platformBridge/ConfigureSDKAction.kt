package com.intellij.python.sdkConfigurator.backend.impl.platformBridge

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.python.sdkConfigurator.backend.impl.configureSdkAskingUser
import com.intellij.python.sdkConfigurator.backend.impl.configureSdkAskingUserBg
import com.intellij.python.sdkConfigurator.common.enableSDKAutoConfigurator

internal class ConfigureSDKAction : AnAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    if (!enableSDKAutoConfigurator) {
      return
    }
    configureSdkAskingUserBg(project)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null && enableSDKAutoConfigurator
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}


