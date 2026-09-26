// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.editor

import com.intellij.ide.settings.RemoteSettingInfo
import com.intellij.ide.settings.RemoteSettingInfoProvider

class WebEditorOptionsRemoteSettingInfoProvider: RemoteSettingInfoProvider {
  override fun getRemoteSettingsInfo(): Map<String, RemoteSettingInfo>  = mapOf(
    SETTINGS_NAME to RemoteSettingInfo(RemoteSettingInfo.Direction.InitialFromFrontend),
  )

  companion object {
    const val SETTINGS_NAME = "XmlEditorOptions"
  }
}