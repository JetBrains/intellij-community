package org.jetbrains.plugins.textmate.configuration

import com.intellij.ide.settings.RemoteSettingInfo
import com.intellij.ide.settings.RemoteSettingInfoProvider

class TextMateBuiltinBundlesSettingsRemoteInfoProvider : RemoteSettingInfoProvider {
  override fun getRemoteSettingsInfo(): Map<String, RemoteSettingInfo> =
    mapOf("TextMateBuiltinBundlesSettings" to RemoteSettingInfo(RemoteSettingInfo.Direction.OnlyFromBackend, false))
}