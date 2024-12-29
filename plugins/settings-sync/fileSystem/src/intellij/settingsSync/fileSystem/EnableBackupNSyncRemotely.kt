package intellij.settingsSync.fileSystem

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.settingsSync.SettingsSyncLocalSettings
import com.intellij.settingsSync.SettingsSyncSettings
import com.intellij.settingsSync.UpdateResult.*
import com.intellij.settingsSync.config.SettingsSyncEnabler
import com.intellij.util.containers.toMutableSmartList

class EnableBackupNSyncRemotely : DumbAwareAction() {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    ApplicationManager.getApplication().invokeLater {
      val folderDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        .withTitle("Select root folder")
      val chooser = FileChooserFactory.getInstance().createPathChooser(folderDescriptor, null, null)
      chooser.choose(null) {
        val path = it.single().path
        val availableAccounts = (PropertiesComponent.getInstance().getList("FSAuthServiceAccounts") ?: emptyList()).toMutableSmartList()
        availableAccounts.add(path)
        SettingsSyncLocalSettings.getInstance().userId = path
        SettingsSyncLocalSettings.getInstance().providerCode = "fs"
        PropertiesComponent.getInstance().setList("FSAuthServiceAccounts", availableAccounts)
      }
      SettingsSyncSettings.getInstance().syncEnabled = true
      val enabler = SettingsSyncEnabler()
      val serverState = enabler.getServerState()
      when (serverState) {
        is NoFileOnServer, FileDeletedFromServer -> {
          enabler.pushSettingsToServer()
        }
        is Success -> {
          enabler.getSettingsFromServer(null)
        }
        is Error -> {
          logger<EnableBackupNSyncRemotely>().error(serverState.message)
        }
      }
    }
  }



}