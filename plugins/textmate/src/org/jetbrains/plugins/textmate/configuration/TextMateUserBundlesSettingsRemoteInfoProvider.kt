package org.jetbrains.plugins.textmate.configuration

import com.intellij.ide.settings.RemoteSettingInfo
import com.intellij.ide.settings.RemoteSettingInfoProvider

class TextMateUserBundlesSettingsRemoteInfoProvider : RemoteSettingInfoProvider {
  override fun getRemoteSettingsInfo(): Map<String, RemoteSettingInfo> =
    mapOf("TextMateUserBundlesSettings" to RemoteSettingInfo(RemoteSettingInfo.Direction.OnlyFromBackend, false))
}