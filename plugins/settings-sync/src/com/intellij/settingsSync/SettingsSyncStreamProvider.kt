package com.intellij.settingsSync

import com.intellij.configurationStore.StreamProvider
import com.intellij.configurationStore.getPerOsSettingsStorageFolderName
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.RoamingType
import com.intellij.util.io.*
import java.io.InputStream
import java.nio.file.Path

internal class SettingsSyncStreamProvider(private val application: Application,
                                          private val rootConfig: Path) : StreamProvider {
  private val appConfig: Path get() = rootConfig.resolve(PathManager.OPTIONS_DIRECTORY)

  override val isExclusive: Boolean
    get() = true

  override val enabled: Boolean
    get() = isSettingsSyncEnabledByKey() && SettingsSyncMain.isAvailable() && isSettingsSyncEnabledInSettings()

  override fun isApplicable(fileSpec: String, roamingType: RoamingType): Boolean {
    return true
  }

  override fun write(fileSpec: String, content: ByteArray, size: Int, roamingType: RoamingType) {
    val file = getFileRelativeToRootConfig(fileSpec)

    // todo can we call default "stream provider" instead of duplicating logic?
    rootConfig.resolve(file).write(content, 0, size)

    if (!isSyncEnabled(fileSpec, roamingType)) {
      return
    }

    val snapshot = SettingsSnapshot(setOf(FileState(file, content, size)))
    application.messageBus.syncPublisher(SETTINGS_CHANGED_TOPIC).settingChanged(SyncSettingsEvent.IdeChange(snapshot))
  }

  override fun read(fileSpec: String, roamingType: RoamingType, consumer: (InputStream?) -> Unit): Boolean {
    consumer(appConfig.resolve(fileSpec).inputStreamIfExists())
    return true
  }

  override fun processChildren(path: String,
                               roamingType: RoamingType,
                               filter: (name: String) -> Boolean,
                               processor: (name: String, input: InputStream, readOnly: Boolean) -> Boolean): Boolean {
    rootConfig.resolve(path).directoryStreamIfExists({ filter(it.fileName.toString()) }) { fileStream ->
      for (file in fileStream) {
        if (!file.inputStream().use { processor(file.fileName.toString(), it, false) }) {
          break
        }
      }
    }
    // this method is called only for reading => no SETTINGS_CHANGED_TOPIC message is needed
    return true
  }

  override fun delete(fileSpec: String, roamingType: RoamingType): Boolean {
    rootConfig.resolve(getFileRelativeToRootConfig(fileSpec)).delete()
    // todo shall we sent the message to the SETTINGS_CHANGED_TOPIC? which one?
    return true
  }

  private fun getFileRelativeToRootConfig(fileSpecPassedToProvider: String): String {
    // For PersistentStateComponents the fileSpec is passed without the 'options' folder, e.g. 'editor.xml' or 'mac/keymaps.xml'
    // OTOH for schemas it is passed together with the containing folder, e.g. 'keymaps/mykeymap.xml'
    return if (!fileSpecPassedToProvider.contains("/") || fileSpecPassedToProvider.startsWith(getPerOsSettingsStorageFolderName() + "/")) {
      PathManager.OPTIONS_DIRECTORY + "/" + fileSpecPassedToProvider
    }
    else {
      fileSpecPassedToProvider
    }
  }
}
