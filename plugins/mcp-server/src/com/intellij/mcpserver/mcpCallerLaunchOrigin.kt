// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver

import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.statistics.McpCallerLaunchOrigin
import org.jetbrains.annotations.ApiStatus

/**
 * Whether the IDE launched the agent behind a session.
 *
 * `localAgentId` is set only when the IDE opens the session itself, for an agent it started, so its absence is the
 * signal that the caller is a client the IDE did not launch. That distinction is the whole point: an external client
 * is observed only through its MCP calls, and rows from it must not be averaged in with rows from sessions where a
 * full trajectory exists.
 */
@ApiStatus.Internal
fun launchOriginOf(sessionOptions: McpServerService.McpSessionOptions?): McpCallerLaunchOrigin = when {
  sessionOptions == null -> McpCallerLaunchOrigin.UNKNOWN
  sessionOptions.localAgentId != null -> McpCallerLaunchOrigin.IDE_LAUNCHED
  else -> McpCallerLaunchOrigin.EXTERNAL_CLIENT
}
