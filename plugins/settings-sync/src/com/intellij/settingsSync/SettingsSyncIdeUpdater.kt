package com.intellij.settingsSync

import com.intellij.application.options.editor.EditorOptionsPanel
import com.intellij.codeInsight.hints.ParameterHintsPassFactory
import com.intellij.configurationStore.ComponentStoreImpl
import com.intellij.configurationStore.StateStorageManagerImpl
import com.intellij.configurationStore.XmlElementStorage
import com.intellij.configurationStore.schemeManager.SchemeManagerFactoryBase
import com.intellij.configurationStore.schemeManager.SchemeManagerImpl
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.UISettings.Companion.instance
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.PathManager.OPTIONS_DIRECTORY
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.IconLoader
import com.intellij.settingsSync.plugins.SettingsSyncPluginManager
import com.intellij.ui.JBColor
import com.intellij.util.SmartList
import com.intellij.util.io.delete
import com.intellij.util.io.write
import com.intellij.util.ui.StartupUiUtil
import java.nio.file.Path

internal class SettingsSyncIdeUpdater(private val componentStore: ComponentStoreImpl, private val rootConfig: Path) {

  companion object {
    val LOG = logger<SettingsSyncIdeUpdater>()
  }

  fun settingsLogged(snapshot: SettingsSnapshot) {
    // todo race between this code and SettingsSyncStreamProvider.write which can write other user settings at the same time

    // 1. update SettingsSyncSettings first to apply changes in categories
    val settingsSyncFileState = snapshot.fileStates.find { it.file == "$OPTIONS_DIRECTORY/${SettingsSyncSettings.FILE_SPEC}" }
    if (settingsSyncFileState != null) {
      updateSettings(listOf(settingsSyncFileState))
    }

    // 2. update plugins
    val pluginsFileState = snapshot.fileStates.find { it.file == "$OPTIONS_DIRECTORY/${SettingsSyncPluginManager.FILE_SPEC}" }
    if (pluginsFileState != null) {
      val pluginManager = SettingsSyncPluginManager.getInstance()
      pluginManager.doWithNoUpdateFromIde {
        updateSettings(listOf(pluginsFileState))
        pluginManager.pushChangesToIde()
      }
    }

    // 3. after that update the rest of changed settings
    val regularFileStates = snapshot.fileStates.filter { it !=  settingsSyncFileState && it != pluginsFileState }
    updateSettings(regularFileStates)

    invokeLater { updateUI() }
  }

  private fun updateSettings(fileStates: Collection<FileState>) {
    val changedFileSpecs = ArrayList<String>()
    val deletedFileSpecs = ArrayList<String>()
    for (fileState in fileStates) {
      val fileSpec = fileState.file.removePrefix("$OPTIONS_DIRECTORY/")
      if (isSyncEnabled(fileSpec, RoamingType.DEFAULT)) {
        val file = rootConfig.resolve(fileState.file)
        // todo handle exceptions when modifying the file system
        when (fileState) {
          is FileState.Modified -> {
            file.write(fileState.content, 0, fileState.size)
            changedFileSpecs.add(fileSpec)
          }
          is FileState.Deleted -> {
            file.delete()
            deletedFileSpecs.add(fileSpec)
          }
        }
      }
    }

    invokeAndWaitIfNeeded { reloadComponents(changedFileSpecs, deletedFileSpecs) }
  }

  private fun reloadComponents(changedFileSpecs: List<String>, deletedFileSpecs: List<String>) {
    val schemeManagerFactory = SchemeManagerFactory.getInstance() as SchemeManagerFactoryBase
    val storageManager = componentStore.storageManager as StateStorageManagerImpl
    val (changed, deleted) = storageManager.getCachedFileStorages(changedFileSpecs, deletedFileSpecs, null)

    val schemeManagersToReload = SmartList<SchemeManagerImpl<*, *>>()
    schemeManagerFactory.process {
      schemeManagersToReload.add(it)
    }

    val changedComponentNames = LinkedHashSet<String>()
    updateStateStorage(changedComponentNames, changed, false)
    updateStateStorage(changedComponentNames, deleted, true)

    for (schemeManager in schemeManagersToReload) {
      schemeManager.reload()
    }

    val notReloadableComponents = componentStore.getNotReloadableComponents(changedComponentNames)
    componentStore.reinitComponents(changedComponentNames, changed.toSet(), notReloadableComponents)
  }

  private fun updateStateStorage(changedComponentNames: MutableSet<String>, stateStorages: Collection<StateStorage>, deleted: Boolean) {
    for (stateStorage in stateStorages) {
      try {
        // todo maybe we don't need "from stream provider" here since we modify the settings in place?
        (stateStorage as XmlElementStorage).updatedFromStreamProvider(changedComponentNames, deleted)
      }
      catch (e: Throwable) {
        LOG.error(e)
      }
    }
  }

  // todo copypasted from the CloudConfigManager
  private fun updateUI() {
    // TODO: separate and move this code to specific managers
    val lafManager = LafManager.getInstance()
    val lookAndFeel = lafManager.currentLookAndFeel
    if (lookAndFeel != null) {
      lafManager.setCurrentLookAndFeel(lookAndFeel, true)
    }
    val darcula = StartupUiUtil.isUnderDarcula()
    JBColor.setDark(darcula)
    IconLoader.setUseDarkIcons(darcula)
    ActionToolbarImpl.updateAllToolbarsImmediately()
    lafManager.updateUI()
    instance.fireUISettingsChanged()
    ParameterHintsPassFactory.forceHintsUpdateOnNextPass()
    EditorOptionsPanel.reinitAllEditors()
    EditorOptionsPanel.restartDaemons()
    for (project in ProjectManager.getInstance().openProjects) {
      ProjectView.getInstance(project).refresh()
    }
  }
}