// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.util

import com.intellij.mcpserver.impl.McpServerService
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetSettings
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import kotlinx.coroutines.launch


const val MCP_STATUS_BAR_WIDGET_ID: String = "McpServerStatusBarWidget"

/**
 * Enables the MCP status bar widget, unless the user has explicitly disabled it before.
 * Runs asynchronously in the background.
 */
fun enableIfNotExplicitlyDisabled() {
  McpServerService.getInstance().cs.launch {
    val settings = serviceOrNull<StatusBarWidgetSettings>() ?: return@launch
    if (settings.isExplicitlyDisabled(MCP_STATUS_BAR_WIDGET_ID)) return@launch
    val factory = StatusBarWidgetFactory.EP_NAME.getIterable().find { it?.getId() == MCP_STATUS_BAR_WIDGET_ID } ?: return@launch
    settings.setEnabled(factory, true)
    for (project in ProjectManagerEx.getOpenProjects()) {
      project.serviceOrNull<StatusBarWidgetsManager>()?.updateWidget(factory)
    }
  }
}