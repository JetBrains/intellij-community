// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.elicitation

import com.intellij.openapi.project.Project

/**
 * Turns the ordered [ElicitationMessagePart]s of a form into the single message string that the MCP
 * `elicitation/create` request carries.
 *
 * The implementation is chosen by the [McpElicitationProvider] serving the call, because the right
 * encoding depends on what the client's UI can show: a terminal renders ANSI escapes, a chat UI
 * renders Markdown and prints the escapes literally.
 */
interface ElicitationFormRenderer {
  fun render(parts: List<ElicitationMessagePart>, project: Project? = null): String
}
