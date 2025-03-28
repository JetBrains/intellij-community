// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.session.rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.reworked.session.ShellStartupOptionsDto
import org.jetbrains.plugins.terminal.block.reworked.session.TerminalSessionTab

@ApiStatus.Internal
@Rpc
interface TerminalTabsManagerApi : RemoteApi<Unit> {
  suspend fun getTerminalTabs(projectId: ProjectId): List<TerminalSessionTab>

  suspend fun createNewTerminalTab(projectId: ProjectId): TerminalSessionTab

  suspend fun startTerminalSessionForTab(projectId: ProjectId, tabId: Int, options: ShellStartupOptionsDto): TerminalSessionTab

  suspend fun closeTerminalTab(projectId: ProjectId, tabId: Int)

  suspend fun renameTerminalTab(projectId: ProjectId, tabId: Int, newName: String, isUserDefinedName: Boolean)

  companion object {
    @JvmStatic
    suspend fun getInstance(): TerminalTabsManagerApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<TerminalTabsManagerApi>())
    }
  }
}