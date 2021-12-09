package com.intellij.settingsSync

import com.intellij.configurationStore.*
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
    get() = true

  override fun isApplicable(fileSpec: String, roamingType: RoamingType): Boolean {
    return true
  }

  override fun write(fileSpec: String, content: ByteArray, size: Int, roamingType: RoamingType) {
    val file = PathManager.OPTIONS_DIRECTORY + "/" + fileSpec
    rootConfig.resolve(file).write(content, 0, size)

    if (roamingType == RoamingType.DISABLED) {
      return
    }

    val snap = SettingsSnapshot(setOf(FileState(file, content, size)))
    application.messageBus.syncPublisher(SETTINGS_CHANGED_TOPIC).settingChanged(SettingsChangeEvent(ChangeSource.FROM_LOCAL, snap))
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
    return true
  }

  override fun delete(fileSpec: String, roamingType: RoamingType): Boolean {
    rootConfig.resolve(fileSpec).delete()
    return true
  }
}
