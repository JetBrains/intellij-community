package com.intellij.settingsSync

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.configurationStore.*
import com.intellij.configurationStore.schemeManager.SchemeManagerFactoryBase
import com.intellij.configurationStore.schemeManager.SchemeManagerImpl
import com.intellij.openapi.application.PathManager.OPTIONS_DIRECTORY
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.settingsSync.SettingsSnapshot.MetaInfo
import com.intellij.settingsSync.plugins.SettingsSyncPluginManager
import com.intellij.util.SmartList
import com.intellij.util.SystemProperties
import com.intellij.util.io.*
import java.io.InputStream
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock
import kotlin.io.path.pathString
import kotlin.random.Random

internal class SettingsSyncIdeMediatorImpl(private val componentStore: ComponentStoreImpl,
                                           private val rootConfig: Path,
                                           private val enabledCondition: () -> Boolean) : StreamProvider, SettingsSyncIdeMediator {

  companion object {
    val LOG = logger<SettingsSyncIdeMediatorImpl>()
  }

  private val appConfig: Path get() = rootConfig.resolve(OPTIONS_DIRECTORY)
  private val fileSpecsToLocks = ConcurrentCollectionFactory.createConcurrentMap<String, ReadWriteLock>()

  override val isExclusive: Boolean
    get() = true

  override val enabled: Boolean
    get() = enabledCondition()

  override fun isApplicable(fileSpec: String, roamingType: RoamingType): Boolean {
    return true
  }

  override fun applyToIde(snapshot: SettingsSnapshot) {
    // 1. update SettingsSyncSettings first to apply changes in categories
    val settingsSyncFileState = snapshot.fileStates.find { it.file == "$OPTIONS_DIRECTORY/${SettingsSyncSettings.FILE_SPEC}" }
    if (settingsSyncFileState != null) {
      writeStatesToAppConfig(listOf(settingsSyncFileState))
    }

    if (SystemProperties.getBooleanProperty("settings.sync.test.fail.on.settings.apply", false)) {
      if (Random.nextBoolean()) {
        throw IllegalStateException("Applying settings failed")
      }
    }

    // 2. update plugins
    if (snapshot.plugins != null) {
      SettingsSyncPluginManager.getInstance().pushChangesToIde(snapshot.plugins)
    }

    // 3. after that update the rest of changed settings
    val regularFileStates = snapshot.fileStates.filter { it != settingsSyncFileState }
    writeStatesToAppConfig(regularFileStates)
  }

  override fun activateStreamProvider() {
    componentStore.storageManager.addStreamProvider(this, true)
  }

  override fun removeStreamProvider() {
    componentStore.storageManager.removeStreamProvider(this::class.java)
  }

  override fun getInitialSnapshot(appConfigPath: Path, lastSavedSnapshot: SettingsSnapshot): SettingsSnapshot {
    val exportableItems = getExportableComponentsMap(isComputePresentableNames = false, componentStore.storageManager,
                                                     withExportable = false)
      .filterKeys { isSyncEnabled(it.rawFileSpec, RoamingType.DEFAULT) }
    val filesToExport = getExportableItemsFromLocalStorage(exportableItems, componentStore.storageManager).keys

    val fileStates = collectFileStatesFromFiles(filesToExport, appConfigPath)
    LOG.debug("Collected files for the following fileSpecs: ${fileStates.map { it.file }}")

    val pluginsState = SettingsSyncPluginManager.getInstance().updateStateFromIdeOnStart(lastSavedSnapshot.plugins)
    LOG.debug("Collected following plugin state: $pluginsState")
    return SettingsSnapshot(MetaInfo(Instant.now(), getLocalApplicationInfo()), fileStates, pluginsState)
  }

  override fun write(fileSpec: String, content: ByteArray, size: Int, roamingType: RoamingType) {
    val file = getFileRelativeToRootConfig(fileSpec)

    writeUnderLock(file) {
      rootConfig.resolve(file).write(content, 0, size)
    }

    val syncEnabled = isSyncEnabled(fileSpec, roamingType)
    LOG.debug("Sync is ${if (syncEnabled) "enabled" else "disabled"} for $fileSpec ($file)")
    if (!syncEnabled) {
      return
    }

    val snapshot = SettingsSnapshot(MetaInfo(Instant.now(), getLocalApplicationInfo()),
                                    setOf(FileState.Modified(file, content, size)), plugins = null)
    SettingsSyncEvents.getInstance().fireSettingsChanged(SyncSettingsEvent.IdeChange(snapshot))
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
    if (roamingType == RoamingType.DISABLED) {
      return false
    }

    val adjustedSpec = getFileRelativeToRootConfig(fileSpec)
    val file = rootConfig.resolve(adjustedSpec)
    if (!file.exists()) {
      LOG.debug("File $file doesn't exist, no need to delete")
      return true
    }

    val deleted = writeUnderLock(adjustedSpec) {
      deleteOrLogError(file)
    }
    if (deleted) {
      val snapshot = SettingsSnapshot(MetaInfo(Instant.now(), getLocalApplicationInfo()),
                                      setOf(FileState.Deleted(adjustedSpec)), plugins = null)
      SettingsSyncEvents.getInstance().fireSettingsChanged(SyncSettingsEvent.IdeChange(snapshot))
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

  private fun writeStatesToAppConfig(fileStates: Collection<FileState>) {
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
      if (schemeManager.fileSpec == "colors") {
        EditorColorsManager.getInstance().reloadKeepingActiveScheme()
      }
      else {
        schemeManager.reload()
      }
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
}