package com.intellij.mcpserver.elicitation

import com.intellij.openapi.project.Project


/**
 * [ElicitationFormRenderer] emitting plain text, for clients that show the message without any formatting.
 */
object TextElicitationFormRenderer : ElicitationFormRenderer {

  /**
   * Renders message [parts] into one simple string for a terminal.
   * Parts are joined with no separator, so put line breaks inside [ElicitationMessagePart.Text].
   */
  override fun render(parts: List<ElicitationMessagePart>, project: Project?): String =
    parts.joinToString("") { it.text }

}
