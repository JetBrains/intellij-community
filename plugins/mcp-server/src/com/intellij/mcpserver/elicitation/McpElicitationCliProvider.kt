// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.elicitation

/**
 * Built-in [McpElicitationProvider] for CLI sessions, rendering the message as ANSI for clients that
 * show it as terminal text.
 */
class McpElicitationCliProvider : McpTransportElicitationProvider() {

  override val renderer: ElicitationFormRenderer get() = AnsiElicitationFormRenderer

  override fun isApplicable(kind: McpElicitationKind): Boolean = kind == McpElicitationKind.CLI
}
