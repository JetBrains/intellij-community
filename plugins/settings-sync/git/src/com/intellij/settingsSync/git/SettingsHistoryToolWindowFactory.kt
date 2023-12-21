package com.intellij.settingsSync.git

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.settingsSync.SettingsSyncBundle
import com.intellij.settingsSync.SettingsSyncMain
import com.intellij.settingsSync.isSettingsSyncEnabledByKey
import git4idea.GitVcs
import git4idea.log.showExternalGitLogInToolwindow

class SettingsHistoryToolWindowFactory : ToolWindowFactory, DumbAware {
  companion object {
    const val ID = "Settings Sync History"
  }

  override suspend fun isApplicableAsync(project: Project): Boolean {
    return isSettingsSyncEnabledByKey() && Registry.`is`("settingsSync.ui.new.toolwindow.show")
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    ApplicationManager.getApplication().executeOnPooledThread {
      val settingsSyncStorage = SettingsSyncMain.getInstance().controls.settingsSyncStorage
      val virtualFile = VfsUtil.findFile(settingsSyncStorage, true)
      if (virtualFile == null) {
        ApplicationManager.getApplication().invokeLater {
          Messages.showErrorDialog(SettingsSyncBundle.message("history.error.message"), SettingsSyncBundle.message("history.dialog.title"))
        }
        return@executeOnPooledThread
      }

      showExternalGitLogInToolwindow(project, toolWindow, { SettingsHistoryLogUiFactory() }, GitVcs.getInstance(project),
                                     listOf(virtualFile), "", "")
    }
  }
}