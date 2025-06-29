// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performanceTesting.frontend

import com.jetbrains.performancePlugin.CommandProvider
import com.jetbrains.performancePlugin.CreateCommand
import com.intellij.performanceTesting.frontend.commands.ShowRecentFilesCommand

/**
 * Provides commands that are compatible with both the monolith and frontend sides in cwm/split/remdev installations
 */
internal class FrontendCommandProvider : CommandProvider {
  override fun getCommands(): Map<String, CreateCommand> {
    return mapOf(
      ShowRecentFilesCommand.PREFIX to CreateCommand(::ShowRecentFilesCommand),
    )
  }
}