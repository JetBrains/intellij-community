package org.jetbrains.yaml.smart

import com.intellij.ide.settings.RemoteSettingInfo
import com.intellij.ide.settings.RemoteSettingInfoProvider

class YAMLRemoteSettingInfoProvider : RemoteSettingInfoProvider {
  override fun getRemoteSettingsInfo(): Map<String, RemoteSettingInfo> = mapOf(
    "YamlEditorOptions" to RemoteSettingInfo(RemoteSettingInfo.Direction.InitialFromFrontend)
  )

  override fun getPluginIdMapping(endpoint: RemoteSettingInfo.Endpoint): Map<String, String> = when (endpoint) {
    RemoteSettingInfo.Endpoint.Backend -> mapOf("org.jetbrains.plugins.yaml.YamlEditorOptions" to "org.jetbrains.plugins.yaml.frontend")
    else -> mapOf("org.jetbrains.plugins.yaml.frontend.YamlEditorOptions" to "org.jetbrains.plugins.yaml")
  }
}