// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.elicitation

import com.intellij.mcpserver.settings.McpServerSettings

/**
 * Built-in [McpElicitationProvider] for CLI sessions.
 * Renders the message as ANSI when [McpServerSettings.enableTerminalAnsiHighlighting] is enabled, otherwise as plain text.
 */
class McpElicitationCliProvider : McpTransportElicitationProvider() {

  override val renderer: ElicitationFormRenderer get() =
    if (McpServerSettings.getInstance().enableTerminalAnsiHighlighting) {
      AnsiElicitationFormRenderer
    }
    else {
      TextElicitationFormRenderer
    }

  override fun isApplicable(kind: McpElicitationKind): Boolean = kind == McpElicitationKind.CLI
}
