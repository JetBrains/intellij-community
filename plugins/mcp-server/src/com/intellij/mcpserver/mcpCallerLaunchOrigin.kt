// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver

import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.statistics.McpCallerLaunchOrigin
import org.jetbrains.annotations.ApiStatus

/**
 * Whether the IDE launched the agent behind a session. `localAgentId` is set only when the IDE opens the session itself,
 * so its absence means a client the IDE did not launch — one observed through its MCP calls alone, which must not be
 * averaged in with sessions that have a full trajectory.
 */
@ApiStatus.Internal
fun launchOriginOf(sessionOptions: McpServerService.McpSessionOptions?): McpCallerLaunchOrigin = when {
  sessionOptions == null -> McpCallerLaunchOrigin.UNKNOWN
  sessionOptions.localAgentId != null -> McpCallerLaunchOrigin.IDE_LAUNCHED
  else -> McpCallerLaunchOrigin.EXTERNAL_CLIENT
}
