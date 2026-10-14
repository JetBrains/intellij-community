// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver

/**
 * The client that started the agent of an MCP call, for example the Air chat.
 *
 * The client sets the environment variable [NAME] for the agent process. The MCP client configuration of the agent
 * sends the variable as a header with the same name. A terminal and an external client do not set the variable.
 */
object McpLaunchOrigin {
  const val NAME: String = "IJ_MCP_LAUNCH_ORIGIN"

  const val AIR_CHAT: String = "air-chat"
}

/** The launch origin of the call, or `null` when the agent sent no value. */
val McpCallHeaders?.launchOrigin: String?
  get() = this?.get(McpLaunchOrigin.NAME)?.firstOrNull()?.takeIf { it.isNotEmpty() }
