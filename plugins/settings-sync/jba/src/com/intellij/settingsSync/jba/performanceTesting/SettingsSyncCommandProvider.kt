package com.intellij.settingsSync.jba.performanceTesting

import com.jetbrains.performancePlugin.CommandProvider
import com.jetbrains.performancePlugin.CreateCommand

class SettingsSyncCommandProvider: CommandProvider {
    override fun getCommands(): MutableMap<String, CreateCommand> {
      return mutableMapOf(
        EnableSettingsSyncCommand.PREFIX to CreateCommand(::EnableSettingsSyncCommand),
        GetSettingsFromServerCommand.PREFIX to CreateCommand(::GetSettingsFromServerCommand),
        PushSettingsToServerCommand.PREFIX to CreateCommand(::PushSettingsToServerCommand),
        DisableSettingsSyncCommand.PREFIX to CreateCommand(::DisableSettingsSyncCommand),
      )
    }
}