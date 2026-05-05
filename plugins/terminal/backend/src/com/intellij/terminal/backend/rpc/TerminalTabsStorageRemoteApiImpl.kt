// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.backend.rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import org.jetbrains.plugins.terminal.settings.impl.TerminalSessionPersistedTabDto
import org.jetbrains.plugins.terminal.settings.impl.TerminalTabsStorage
import org.jetbrains.plugins.terminal.settings.impl.TerminalTabsStorageRemoteApi

internal class TerminalTabsStorageRemoteApiImpl : TerminalTabsStorageRemoteApi {
  override suspend fun getStoredTabs(projectId: ProjectId): List<TerminalSessionPersistedTabDto> {
    val project = projectId.findProjectOrNull() ?: return emptyList()
    return TerminalTabsStorage.getInstance(project).getStoredTabs().map { tab ->
      TerminalSessionPersistedTabDto(
        name = tab.name,
        isUserDefinedName = tab.isUserDefinedName,
        shellCommand = tab.shellCommand,
        workingDirectory = tab.workingDirectory,
        envVariables = tab.envVariables,
        processType = tab.processType,
      )
    }
  }
}