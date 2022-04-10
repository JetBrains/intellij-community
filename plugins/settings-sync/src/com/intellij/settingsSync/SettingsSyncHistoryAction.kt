package com.intellij.settingsSync

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtil
import git4idea.GitVcs
import git4idea.log.showExternalGitLogInDialog

class SettingsSyncHistoryAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val settingsSyncStorage = SettingsSyncMain.getInstance().controls.settingsSyncStorage
    val virtualFile = VfsUtil.findFile(settingsSyncStorage, true)
    if (virtualFile == null) {
      Messages.showErrorDialog(SettingsSyncBundle.message("history.error.message"), SettingsSyncBundle.message("history.dialog.title"))
      return
    }
    showExternalGitLogInDialog(project, GitVcs.getInstance(project), listOf(virtualFile), SettingsSyncBundle.message("history.dialog.title"))
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null && isSettingsSyncEnabledByKey()
  }
}