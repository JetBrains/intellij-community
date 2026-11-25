// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.frontend.util

import com.intellij.ide.impl.ProjectUtil
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.McpUnauthorizedRequestNotifier
import com.intellij.mcpserver.impl.IJ_MCP_AUTH_TOKEN
import com.intellij.mcpserver.settings.McpServerSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.SwingConstants

private const val MCP_SERVER_SETTINGS_ID = "com.intellij.mcpserver.settings"

private const val MESSAGE_WIDTH = 450

/**
 * Tells the user that an application reached the global MCP server without the token, and explains where to put it.
 *
 * The dialog appears at most once per IDE session, because a scanner knocking on the port would otherwise raise it
 * for every request. The second button of the dialog silences it for good; the setting behind that button is
 * [McpServerSettings.notifyOnUnauthorizedAccess]. A headless or test host has nobody to ask, so there the attempt
 * only goes to the log.
 */
internal class McpUnauthorizedRequestNotifierImpl : McpUnauthorizedRequestNotifier {
  private val shownThisSession = AtomicBoolean(false)

  override suspend fun notifyUnauthorizedRequest(port: Int) {
    val application = ApplicationManager.getApplication()
    if (application.isUnitTestMode || application.isHeadlessEnvironment) return
    val settings = McpServerSettings.getInstance()
    if (!settings.notifyOnUnauthorizedAccess) return
    if (!shownThisSession.compareAndSet(false, true)) return
    withContext(Dispatchers.EDT) { // TODO IJPL-262703
      val dialog = UnauthorizedRequestDialog(ProjectUtil.getActiveProject(), port)
      dialog.show()
      if (dialog.doNotShowAgain) {
        settings.notifyOnUnauthorizedAccess = false
      }
    }
  }
}

/**
 * A click on the link in the message closes the dialog first, and then opens the settings page of the MCP server.
 * The dialog has its own class because [Messages.showDialog] cannot react to a click on a link.
 */
private class UnauthorizedRequestDialog(private val project: Project?, private val port: Int) : DialogWrapper(project, false) {
  var doNotShowAgain: Boolean = false
    private set

  init {
    title = McpServerBundle.message("dialog.title.mcp.server.unauthorized")
    setOKButtonText(McpServerBundle.message("dialog.mcp.server.unauthorized.ok"))
    init()
  }

  override fun createCenterPanel(): JComponent {
    val message = SwingHelper.createHtmlLabel(
      McpServerBundle.message("dialog.message.mcp.server.unauthorized", port.toString(), IJ_MCP_AUTH_TOKEN),
      null,
    ) { openSettings() }
    message.setSize(JBUI.scale(MESSAGE_WIDTH), Short.MAX_VALUE.toInt())
    message.preferredSize = Dimension(JBUI.scale(MESSAGE_WIDTH), message.preferredSize.height)
    val icon = JBLabel(Messages.getWarningIcon()).apply { verticalAlignment = SwingConstants.TOP }
    return JBUI.Panels.simplePanel(JBUI.scale(10), 0)
      .addToLeft(icon)
      .addToCenter(message)
  }

  override fun createActions(): Array<Action> {
    val doNotShowAgainAction = object : DialogWrapperAction(McpServerBundle.message("dialog.mcp.server.unauthorized.dont.show.again")) {
      override fun doAction(e: ActionEvent?) {
        doNotShowAgain = true
        close(NEXT_USER_EXIT_CODE)
      }
    }
    return arrayOf(okAction, doNotShowAgainAction)
  }

  private fun openSettings() {
    close(OK_EXIT_CODE)
    ShowSettingsUtil.getInstance().showSettingsDialog(
      project,
      { it is SearchableConfigurable && it.id == MCP_SERVER_SETTINGS_ID },
      null,
    )
  }
}
