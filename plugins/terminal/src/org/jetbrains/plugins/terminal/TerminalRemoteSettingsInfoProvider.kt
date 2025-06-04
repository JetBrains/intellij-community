// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.ide.settings.RemoteSettingInfo
import com.intellij.ide.settings.RemoteSettingInfo.Direction.InitialFromFrontend
import com.intellij.ide.settings.RemoteSettingInfoProvider
import org.jetbrains.plugins.terminal.block.BlockTerminalOptions

internal class TerminalRemoteSettingsInfoProvider : RemoteSettingInfoProvider {
  override fun getRemoteSettingsInfo(): Map<String, RemoteSettingInfo> = mapOf(
    TerminalOptionsProvider.COMPONENT_NAME to RemoteSettingInfo(InitialFromFrontend),
    TerminalFontSettingsService.COMPONENT_NAME to RemoteSettingInfo(InitialFromFrontend),
    BlockTerminalOptions.COMPONENT_NAME to RemoteSettingInfo(InitialFromFrontend),
  )
}