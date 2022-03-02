package com.intellij.settingsSync

import com.intellij.application.options.editor.EditorOptionsPanel
import com.intellij.codeInsight.hints.ParameterHintsPassFactory
import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.configurationStore.*
import com.intellij.configurationStore.schemeManager.SchemeManagerFactoryBase
import com.intellij.configurationStore.schemeManager.SchemeManagerImpl
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.UISettings.Companion.getInstance
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.PathManager.OPTIONS_DIRECTORY
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.IconLoader
import com.intellij.settingsSync.plugins.SettingsSyncPluginManager
import com.intellij.ui.JBColor
import com.intellij.util.SmartList
import com.intellij.util.io.*
import com.intellij.util.ui.StartupUiUtil
import java.io.InputStream
import java.nio.file.Path
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock
import kotlin.io.path.pathString

internal class SettingsSyncIdeCommunicator(private val application: Application,
                                           private val componentStore: ComponentStoreImpl,
                                           private val rootConfig: Path) : StreamProvider {

  companion object {
    val LOG = logger<SettingsSyncIdeCommunicator>()
  }

  private val appConfig: Path get() = rootConfig.resolve(OPTIONS_DIRECTORY)
  private val fileSpecsToLocks = ConcurrentCollectionFactory.createConcurrentMap<String, ReadWriteLock>()

  override val isExclusive: Boolean
    get() = true

  override val enabled: Boolean
    get() = isSettingsSyncEnabledByKey() && SettingsSyncMain.isAvailable() && isSettingsSyncEnabledInSettings()

  override fun isApplicable(fileSpec: String, roamingType: RoamingType): Boolean {
    return true
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
    val regularFileStates = snapshot.fileStates.filter { it != settingsSyncFileState && it != pluginsFileState }
    updateSettings(regularFileStates)

    invokeLater { updateUI() }
  }

  override fun write(fileSpec: String, content: ByteArray, size: Int, roamingType: RoamingType) {
    val file = getFileRelativeToRootConfig(fileSpec)

    writeUnderLock(file) {
      rootConfig.resolve(file).write(content, 0, size)
    }

    if (!isSyncEnabled(fileSpec, roamingType)) {
      return
    }

    val snapshot = SettingsSnapshot(setOf(FileState.Modified(file, content, size)))
    application.messageBus.syncPublisher(SETTINGS_CHANGED_TOPIC).settingChanged(SyncSettingsEvent.IdeChange(snapshot))
  }

  override fun read(fileSpec: String, roamingType: RoamingType, consumer: (InputStream?) -> Unit): Boolean {
    val path = appConfig.resolve(fileSpec)
    val adjustedSpec = getFileRelativeToRootConfig(fileSpec)
    return readUnderLock(adjustedSpec) {
      try {
        consumer(path.inputStreamIfExists())
        true
      }
      catch (e: Throwable) {
        val attachment =
          try {
            Attachment(fileSpec, path.readText())
          }
          catch (errorReadingFile: Throwable) {
            Attachment("file-read-error", errorReadingFile)
          }
        LOG.error("Couldn't read $fileSpec", e, attachment)
        false
      }
    }
  }

  override fun processChildren(path: String,
                               roamingType: RoamingType,
                               filter: (name: String) -> Boolean,
                               processor: (name: String, input: InputStream, readOnly: Boolean) -> Boolean): Boolean {
    rootConfig.resolve(path).directoryStreamIfExists({ filter(it.fileName.toString()) }) { fileStream ->
      for (file in fileStream) {
        val shouldProceed = file.inputStream().use { inputStream ->
          val fileSpec = rootConfig.relativize(file).systemIndependentPath
          read(fileSpec) {
            processor(file.fileName.toString(), inputStream, false)
          }
        }
        if (!shouldProceed) {
          break
        }
      }
    }
    // this method is called only for reading => no SETTINGS_CHANGED_TOPIC message is needed
    return true
  }

  override fun delete(fileSpec: String, roamingType: RoamingType): Boolean {
    val adjustedSpec = getFileRelativeToRootConfig(fileSpec)
    val file = rootConfig.resolve(adjustedSpec)
    val deleted = writeUnderLock(adjustedSpec) {
      deleteOrLogError(file)
    }
    if (deleted) {
      val snapshot = SettingsSnapshot(setOf(FileState.Deleted(adjustedSpec)))
      application.messageBus.syncPublisher(SETTINGS_CHANGED_TOPIC).settingChanged(SyncSettingsEvent.IdeChange(snapshot))
    }
    return deleted
  }

  private fun deleteOrLogError(file: Path): Boolean {
    try {
      file.delete()
      return true
    }
    catch (e: Exception) {
      LOG.error("Couldn't delete ${file.pathString}", e)
      return false
    }
  }

  private fun getFileRelativeToRootConfig(fileSpecPassedToProvider: String): String {
    // For PersistentStateComponents the fileSpec is passed without the 'options' folder, e.g. 'editor.xml' or 'mac/keymaps.xml'
    // OTOH for schemas it is passed together with the containing folder, e.g. 'keymaps/mykeymap.xml'
    return if (!fileSpecPassedToProvider.contains("/") || fileSpecPassedToProvider.startsWith(getPerOsSettingsStorageFolderName() + "/")) {
      OPTIONS_DIRECTORY + "/" + fileSpecPassedToProvider
    }
    else {
      fileSpecPassedToProvider
    }
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
            writeUnderLock(fileSpec) {
              file.write(fileState.content, 0, fileState.size)
            }
            changedFileSpecs.add(fileSpec)
          }
          is FileState.Deleted -> {
            writeUnderLock(fileSpec) {
              file.delete()
            }
            deletedFileSpecs.add(fileSpec)
          }
        }
      }
    }

    invokeAndWaitIfNeeded { reloadComponents(changedFileSpecs, deletedFileSpecs) }
  }

  private fun <R> writeUnderLock(fileSpec: String, writingProcedure: () -> R): R {
    return getOrCreateLock(fileSpec).writeLock().withLock {
      writingProcedure()
    }
  }

  private fun <R> readUnderLock(fileSpec: String, readingProcedure: () -> R): R {
    return getOrCreateLock(fileSpec).readLock().withLock {
      readingProcedure()
    }
  }

  private fun getOrCreateLock(fileSpec: String) = fileSpecsToLocks.computeIfAbsent(fileSpec) { ReentrantReadWriteLock() }

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
    getInstance().fireUISettingsChanged()
    ParameterHintsPassFactory.forceHintsUpdateOnNextPass()
    EditorOptionsPanel.reinitAllEditors()
    EditorOptionsPanel.restartDaemons()
    for (project in ProjectManager.getInstance().openProjects) {
      ProjectView.getInstance(project).refresh()
    }
  }
}