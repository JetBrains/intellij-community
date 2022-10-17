package com.intellij.settingsSync

import com.intellij.openapi.application.PathManager
import com.intellij.settingsSync.plugins.SettingsSyncPluginsState
import com.intellij.util.SystemProperties
import org.jetbrains.annotations.ApiStatus
import java.net.InetAddress
import java.time.Instant
import java.util.*

@ApiStatus.Internal
data class SettingsSnapshot(val metaInfo: MetaInfo,
                            val fileStates: Set<FileState>,
                            /* null means there is no information about plugins in this snapshot */ val plugins: SettingsSyncPluginsState?) {

  data class MetaInfo(val dateCreated: Instant, val appInfo: AppInfo?, val isDeleted: Boolean = false)

  data class AppInfo(val applicationId: UUID, val userName: String, val hostName: String, val configFolder: String)

  fun isEmpty(): Boolean = fileStates.isEmpty() && (plugins == null || plugins.plugins.isEmpty())

  fun isDeleted(): Boolean {
    return metaInfo.isDeleted
  }
}

@ApiStatus.Internal
fun getLocalApplicationInfo(): SettingsSnapshot.AppInfo {
  return SettingsSnapshot.AppInfo(SettingsSyncLocalSettings.getInstance().applicationId,
                                  SystemProperties.getUserName(),
                                  InetAddress.getLocalHost().hostName,
                                  PathManager.getConfigPath())
}