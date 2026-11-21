// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.util

import com.intellij.ide.impl.ProjectUtil
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.impl.IJ_MCP_AUTH_TOKEN
import com.intellij.mcpserver.settings.McpServerSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.UiImmediate
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

private const val DO_NOT_SHOW_AGAIN_INDEX = 1

private val shownThisSession = AtomicBoolean(false)

/**
 * Tells the user that an application reached the global MCP server without the token, and explains where to put it.
 *
 * The dialog appears at most once per IDE session, because a scanner knocking on the port would otherwise raise it
 * for every request. The second button of the dialog silences it for good; the setting behind that button is
 * [McpServerSettings.notifyOnUnauthorizedAccess]. A headless or test host has nobody to ask, so there the attempt
 * only goes to the log.
 */
internal suspend fun notifyUnauthorizedMcpRequest(port: Int) {
  val application = ApplicationManager.getApplication()
  if (application.isUnitTestMode || application.isHeadlessEnvironment) return
  val settings = McpServerSettings.getInstance()
  if (!settings.notifyOnUnauthorizedAccess) return
  if (!shownThisSession.compareAndSet(false, true)) return
  withContext(Dispatchers.UiImmediate) {
    val pressed = Messages.showDialog(
      ProjectUtil.getActiveProject(),
      McpServerBundle.message("dialog.message.mcp.server.unauthorized", port, IJ_MCP_AUTH_TOKEN),
      McpServerBundle.message("dialog.title.mcp.server.unauthorized"),
      arrayOf(
        McpServerBundle.message("dialog.mcp.server.unauthorized.ok"),
        McpServerBundle.message("dialog.mcp.server.unauthorized.dont.show.again"),
      ),
      0,
      Messages.getWarningIcon(),
    )
    if (pressed == DO_NOT_SHOW_AGAIN_INDEX) {
      settings.notifyOnUnauthorizedAccess = false
    }
  }
}
