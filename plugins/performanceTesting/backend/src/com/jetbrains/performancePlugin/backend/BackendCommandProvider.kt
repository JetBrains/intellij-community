// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.backend

import com.jetbrains.performancePlugin.CommandProvider
import com.jetbrains.performancePlugin.CreateCommand
import com.jetbrains.performancePlugin.commands.AssertFindUsagesCommand
import com.jetbrains.performancePlugin.commands.AssertFindUsagesEntryCommand
import com.jetbrains.performancePlugin.commands.CollectFilesNotMarkedAsIndex
import com.jetbrains.performancePlugin.commands.CompareIndices
import com.jetbrains.performancePlugin.commands.CompareProjectFiles
import com.jetbrains.performancePlugin.commands.CorruptIndexesCommand
import com.jetbrains.performancePlugin.commands.DumpProjectFiles
import com.jetbrains.performancePlugin.commands.FindUsagesCommand
import com.jetbrains.performancePlugin.commands.FindUsagesInBackgroundCommand
import com.jetbrains.performancePlugin.commands.FindUsagesInToolWindowWaitCommand
import com.jetbrains.performancePlugin.commands.FlushIndexesCommand
import com.jetbrains.performancePlugin.commands.RecoveryActionCommand
import com.jetbrains.performancePlugin.commands.RequestHeavyScanningOnNextStartCommand
import com.jetbrains.performancePlugin.commands.StoreIndices

internal class BackendCommandProvider : CommandProvider {
  override fun getCommands(): Map<String, CreateCommand> {
    return mapOf(
      RecoveryActionCommand.PREFIX to CreateCommand { text, line -> RecoveryActionCommand(text, line) },
      FlushIndexesCommand.PREFIX to CreateCommand { text, line -> FlushIndexesCommand(text, line) },
      AssertFindUsagesCommand.PREFIX to CreateCommand { text, line -> AssertFindUsagesCommand(text, line) },
      AssertFindUsagesEntryCommand.PREFIX to CreateCommand { text, line -> AssertFindUsagesEntryCommand(text, line) },
      DumpProjectFiles.PREFIX to CreateCommand { text, line -> DumpProjectFiles(text, line) },
      CompareProjectFiles.PREFIX to CreateCommand { text, line -> CompareProjectFiles(text, line) },
      CompareIndices.PREFIX to CreateCommand { text, line -> CompareIndices(text, line) },
      StoreIndices.PREFIX to CreateCommand { text, line -> StoreIndices(text, line) },
      FindUsagesCommand.PREFIX to CreateCommand { text, line -> FindUsagesCommand(text, line) },
      FindUsagesInBackgroundCommand.PREFIX to CreateCommand { text, line -> FindUsagesInBackgroundCommand(text, line) },
      FindUsagesInToolWindowWaitCommand.PREFIX to CreateCommand { text, line -> FindUsagesInToolWindowWaitCommand(text, line) },
      RequestHeavyScanningOnNextStartCommand.PREFIX to CreateCommand { text, line -> RequestHeavyScanningOnNextStartCommand(text, line) },
      CollectFilesNotMarkedAsIndex.PREFIX to CreateCommand { text, line -> CollectFilesNotMarkedAsIndex(text, line) },
      CorruptIndexesCommand.PREFIX to CreateCommand { text, line -> CorruptIndexesCommand(text, line) },
    )
  }
}