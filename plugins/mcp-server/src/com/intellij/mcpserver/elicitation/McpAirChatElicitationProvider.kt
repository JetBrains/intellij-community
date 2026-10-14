// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.elicitation

import com.intellij.concurrency.currentThreadContext
import com.intellij.mcpserver.McpLaunchOrigin
import com.intellij.mcpserver.launchOrigin
import com.intellij.mcpserver.mcpCallInfoOrNull

/**
 * Sends the elicitation of a call from the Air chat to the agent, with a Markdown message.
 *
 * The agent forwards it to Air, and the Air card renders Markdown. The provider ignores the elicitation kind, because
 * an agent of the Air chat calls both the global server (the CLI kind) and the private Air gateway (the IDE kind).
 */
class McpAirChatElicitationProvider : McpTransportElicitationProvider() {
  override val renderer: ElicitationFormRenderer get() = MarkdownElicitationFormRenderer

  override fun isApplicable(kind: McpElicitationKind): Boolean =
    currentThreadContext().mcpCallInfoOrNull?.headers.launchOrigin == McpLaunchOrigin.AIR_CHAT
}
