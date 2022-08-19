package com.intellij.settingsSync.git

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.settingsSync.SettingsSyncBundle
import com.intellij.settingsSync.SettingsSyncMain
import com.intellij.settingsSync.isSettingsSyncEnabledByKey
import git4idea.GitVcs
import git4idea.log.showExternalGitLogInToolwindow
import java.util.function.Supplier

class SettingsSyncHistoryAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val settingsSyncStorage = SettingsSyncMain.getInstance().controls.settingsSyncStorage
    val virtualFile = VfsUtil.findFile(settingsSyncStorage, true)
    if (virtualFile == null) {
      Messages.showErrorDialog(SettingsSyncBundle.message("history.error.message"), SettingsSyncBundle.message("history.dialog.title"))
      return
    }

    val toolWindowId = "SettingsSync"
    val toolWindowManager = ToolWindowManager.getInstance(project)
    val toolWindow = toolWindowManager.getToolWindow(toolWindowId) ?: toolWindowManager.registerToolWindow(toolWindowId) {
      stripeTitle = Supplier { SettingsSyncBundle.message("title.settings.sync") }
    }
    showExternalGitLogInToolwindow(project, toolWindow, GitVcs.getInstance(project), listOf(virtualFile),
                                   SettingsSyncBundle.message("history.tab.name"), "")
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null && isSettingsSyncEnabledByKey()
  }
}