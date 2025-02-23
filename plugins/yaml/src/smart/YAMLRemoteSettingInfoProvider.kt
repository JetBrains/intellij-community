package org.jetbrains.yaml.smart

import com.intellij.ide.settings.RemoteSettingInfo
import com.intellij.ide.settings.RemoteSettingInfoProvider

class YAMLRemoteSettingInfoProvider : RemoteSettingInfoProvider {
  override fun getRemoteSettingsInfo(): Map<String, RemoteSettingInfo> = mapOf(
    "YamlEditorOptions" to RemoteSettingInfo(RemoteSettingInfo.Direction.InitialFromFrontend)
  )
}