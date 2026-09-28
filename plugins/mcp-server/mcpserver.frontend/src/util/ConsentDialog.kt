package com.intellij.mcpserver.frontend.util

import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.McpServerConsentUi
import com.intellij.mcpserver.settings.McpServerConsent
import com.intellij.mcpserver.settings.McpServerSettings
import com.intellij.mcpserver.util.getHelpLink
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Asks the user whether the MCP server may run, and returns `true` when the user agreed.
 *
 * An agreement is recorded as [McpServerConsent.GRANTED], so the first-call gate never asks the same user twice. A
 * refusal is not recorded, because here the user only declined to turn the server on, and may still do it later.
 */
fun getConsentDialog(project: Project?): Boolean {
  val granted = MessageDialogBuilder.yesNo(
    McpServerBundle.message("dialog.title.mcp.server.consent"),
    McpServerBundle.message("dialog.message.mcp.server.consent", getHelpLink("mcp-server.html#supported-tools")),
    Messages.getWarningIcon()
  )
    .yesText(McpServerBundle.message("dialog.mcp.server.consent.enable.button"))
    .noText(McpServerBundle.message("dialog.mcp.server.consent.cancel.button"))
    .ask(project)
  if (granted) {
    McpServerSettings.getInstance().consent = McpServerConsent.GRANTED
  }
  return granted
}

/** Serves the first-call consent request of the global MCP server. See [McpServerConsentUi]. */
internal class McpServerConsentUiImpl : McpServerConsentUi {
  override suspend fun askConsent(project: Project?): Boolean = withContext(Dispatchers.EDT) {
    getConsentDialog(project)
  }
}
