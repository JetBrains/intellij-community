package com.intellij.mcpserver.util

import com.intellij.mcpserver.McpServerBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages

fun getConsentDialog(project: Project?): Boolean = MessageDialogBuilder.yesNo(
  McpServerBundle.message("dialog.title.mcp.server.consent"),
  McpServerBundle.message("dialog.message.mcp.server.consent", getHelpLink("mcp-server.html#supported-tools")),
  Messages.getWarningIcon()
)
  .yesText(McpServerBundle.message("dialog.mcp.server.consent.enable.button"))
  .noText(McpServerBundle.message("dialog.mcp.server.consent.cancel.button"))
  .ask(project)