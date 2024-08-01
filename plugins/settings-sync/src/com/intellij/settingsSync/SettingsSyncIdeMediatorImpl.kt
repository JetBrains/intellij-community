package com.intellij.settingsSync

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.configurationStore.*
import com.intellij.openapi.application.PathManager.OPTIONS_DIRECTORY
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
import com.intellij.openapi.util.registry.Registry
import com.intellij.settingsSync.SettingsSnapshot.MetaInfo
import com.intellij.settingsSync.notification.NotificationService
import com.intellij.settingsSync.plugins.SettingsSyncPluginManager
import com.intellij.ui.NewUiValue
import com.intellij.util.io.inputStreamIfExists
import com.intellij.util.io.write
import org.jetbrains.annotations.VisibleForTesting
import java.io.InputStream
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.Predicate
import kotlin.concurrent.withLock
import kotlin.io.path.*

class SettingsSyncIdeMediatorImpl(private val componentStore: ComponentStoreImpl,
                                           private val rootConfig: Path,
                                           private val enabledCondition: () -> Boolean) : StreamProvider, SettingsSyncIdeMediator {

  private val appConfig: Path get() = rootConfig.resolve(OPTIONS_DIRECTORY)
  private val fileSpecsToLocks = ConcurrentCollectionFactory.createConcurrentMap<String, ReadWriteLock>()

  @VisibleForTesting
  internal val files2applyLast = mutableListOf(EditorColorsManagerImpl.STORAGE_NAME)

  override val isExclusive: Boolean
    get() = true

  override val enabled: Boolean
    get() = enabledCondition()

  private val restartRequiredReasons = mutableListOf<RestartReason>()

  init {
    SettingsSyncEvents.getInstance().addListener(object : SettingsSyncEventListener {
      override fun restartRequired(reason: RestartReason) {
        restartRequiredReasons.add(reason)
      }
    })
  }

  override fun isApplicable(fileSpec: String, roamingType: RoamingType): Boolean {
    return roamingType.isRoamable
  }

  override suspend fun applyToIde(snapshot: SettingsSnapshot, settings: SettingsSyncState?) {
    // 1. update SettingsSyncSettings first to apply changes in categories
    val settingsSyncFileState = snapshot.fileStates.find { it.file == "$OPTIONS_DIRECTORY/${SettingsSyncSettings.FILE_SPEC}" }
    if (settings != null) {
      LOG.info("applying sync settings from SettingsSyncState")
      SettingsSyncSettings.getInstance().applyFromState(settings)
    }
    else {
      if (settingsSyncFileState != null) {
        writeStatesToAppConfig(listOf(settingsSyncFileState))
      }
    }

    // 2. update plugins
    if (snapshot.plugins != null) {
      SettingsSyncPluginManager.getInstance().pushChangesToIde(snapshot.plugins)
    }

    // 3. after that update the rest of changed settings
    val regularFileStates = snapshot.fileStates.filter { it != settingsSyncFileState }
    writeStatesToAppConfig(regularFileStates)

    // 4. apply changes from custom providers
    for ((id, state) in snapshot.settingsFromProviders) {
      val provider = findProviderById(id, state)
      if (provider != null) {
        LOG.debug("Applying settings for provider '$id'")
        provider.applyNewSettings(state)
      }
      else {
        LOG.warn("Couldn't find provider for id '$id' and state '${state.javaClass}'")
      }
    }
    notifyRestartNeeded()
  }

  private fun notifyRestartNeeded() {
    val mergedReasons = mergeRestartReasons()
    if (mergedReasons.isEmpty()) return
    NotificationService.getInstance().notifyRestartNeeded(mergedReasons)
  }

  private fun mergeRestartReasons(): List<RestartReason> {
      return restartRequiredReasons.groupBy { it::class.java }.mapNotNull { (clazz, reasons) ->
        when (clazz) {
            RestartForPluginInstall::class.java -> RestartForPluginInstall(reasons.flatMap { (it as RestartForPluginInstall).plugins })
            RestartForPluginEnable::class.java -> RestartForPluginEnable(reasons.flatMap { (it as RestartForPluginEnable).plugins })
            RestartForPluginDisable::class.java -> RestartForPluginDisable(reasons.flatMap { (it as RestartForPluginDisable).plugins })
            else -> null
        }
    }
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
      .filterKeys { isSyncCategoryEnabled(it.rawFileSpec) }
    val filesToExport = getExportableItemsFromLocalStorage(exportableItems, componentStore.storageManager).keys

    val fileStates = collectFileStatesFromFiles(filesToExport, appConfigPath)
    LOG.debug("Collected files for the following fileSpecs: ${fileStates.map { it.file }}")

    val pluginsState = SettingsSyncPluginManager.getInstance().updateStateFromIdeOnStart(lastSavedSnapshot.plugins)
    LOG.debug("Collected following plugin state: $pluginsState")

    val settingsFromProviders = mutableMapOf<String, Any>()
    SettingsProvider.SETTINGS_PROVIDER_EP.forEachExtensionSafe(java.util.function.Consumer {
      val currentSettings = it.collectCurrentSettings()
      if (currentSettings != null) {
        settingsFromProviders[it.id] = currentSettings
      }
    })

    return SettingsSnapshot(MetaInfo(Instant.now(), getLocalApplicationInfo()), fileStates, pluginsState, settingsFromProviders, emptySet())
  }

  override fun write(fileSpec: String, content: ByteArray, roamingType: RoamingType) {
    // we don't really need to check the RoamingType here, it's already checked in isApplicable
    val file = getFileRelativeToRootConfig(fileSpec)

    writeUnderLock(file) {
      rootConfig.resolve(file).write(content)
    }

    val syncEnabled = isSyncCategoryEnabled(fileSpec)
    LOG.debug("Sync is ${if (syncEnabled) "enabled" else "disabled"} for $fileSpec ($file)")
    if (!syncEnabled) {
      return
    }

    val snapshot = SettingsSnapshot(MetaInfo(Instant.now(), getLocalApplicationInfo()),
                                    setOf(FileState.Modified(file, content)), plugins = null, emptyMap(), emptySet())
    SettingsSyncEvents.getInstance().fireSettingsChanged(SyncSettingsEvent.IdeChange(snapshot))
  }

  override fun read(fileSpec: String, roamingType: RoamingType, consumer: (InputStream?) -> Unit): Boolean {
    if (!isApplicable(fileSpec, roamingType)) return false

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
    val folder = rootConfig.resolve(path)
    if (!folder.exists()) return true

    Files.walkFileTree(folder, object : SimpleFileVisitor<Path>() {
      override fun visitFile(file: Path, attrs: BasicFileAttributes?): FileVisitResult {
        if (!filter(file.name)) return FileVisitResult.CONTINUE
        if (!file.isRegularFile()) return FileVisitResult.CONTINUE

        val shouldProceed = file.inputStream().use { inputStream ->
          val fileSpec = rootConfig.relativize(file).invariantSeparatorsPathString
          read(fileSpec) {
            processor(file.fileName.toString(), inputStream, false)
          }
        }
        return if (shouldProceed) FileVisitResult.CONTINUE else FileVisitResult.TERMINATE
      }
    })
    // this method is called only for reading => no SETTINGS_CHANGED_TOPIC message is needed
    return true
  }

  override fun delete(fileSpec: String, roamingType: RoamingType): Boolean {
    if (!isApplicable(fileSpec, roamingType)) {
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
                                      setOf(FileState.Deleted(adjustedSpec)), plugins = null, emptyMap(), emptySet())
      SettingsSyncEvents.getInstance().fireSettingsChanged(SyncSettingsEvent.IdeChange(snapshot))
    }
    return deleted
  }

  private fun deleteOrLogError(file: Path): Boolean {
    try {
      file.deleteExisting()
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
      "$OPTIONS_DIRECTORY/$fileSpecPassedToProvider"
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
      if (isSyncCategoryEnabled(fileSpec)) {
        val file = rootConfig.resolve(fileState.file)
        // todo handle exceptions when modifying the file system
        when (fileState) {
          is FileState.Modified -> {
            writeUnderLock(fileSpec) {
              file.write(fileState.content)
            }
            changedFileSpecs.add(fileSpec)
          }
          is FileState.Deleted -> {
            writeUnderLock(fileSpec) {
              file.deleteIfExists()
            }
            deletedFileSpecs.add(fileSpec)
          }
        }
      }
    }

    invokeAndWaitIfNeeded {
      val (normalChanged, lastChanged) = changedFileSpecs.partition { !(files2applyLast.contains(it)) }
      componentStore.reloadComponents(normalChanged, deletedFileSpecs)
      if (lastChanged.isNotEmpty()) {
        componentStore.reloadComponents(lastChanged, emptyList())
      }
    }
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

  companion object {
    val LOG = logger<SettingsSyncIdeMediatorImpl>()
    internal fun <T: Any> findProviderById(id: String, state: T): SettingsProvider<T>? {
      val provider = SettingsProvider.SETTINGS_PROVIDER_EP.findFirstSafe(Predicate { it.id == id })
      if (provider != null) {
        try {
          @Suppress("UNCHECKED_CAST")
          return provider as SettingsProvider<T>
        }
        catch (e: Exception) {
          LOG.error("Could not cast the provider '${provider.id}' to expected class ${state::class.java}", e)
        }
      }
      else {
        LOG.warn("Couldn't find provider for state class '${state::class.java}'")
      }
      return null
    }
  }
}
