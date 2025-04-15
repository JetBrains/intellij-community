package com.intellij.performanceTesting.frontend

import com.jetbrains.performancePlugin.CommandProvider
import com.jetbrains.performancePlugin.CreateCommand
import com.intellij.performanceTesting.frontend.commands.ShowRecentFilesCommand

internal class FrontendCommandProvider : CommandProvider {
  override fun getCommands(): Map<String, CreateCommand> {
    return mapOf(
      ShowRecentFilesCommand.PREFIX to CreateCommand(::ShowRecentFilesCommand),
    )
  }
}