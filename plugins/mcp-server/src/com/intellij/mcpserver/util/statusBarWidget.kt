package com.intellij.mcpserver.util

/**
 * The id of the MCP status bar widget, shared by the platform factory and by any product that replaces the widget
 * through `com.intellij.mcpServer.statusBarWidgetProvider`. The replacement must report this id, so that the user
 * visibility setting keeps applying to it.
 */
const val MCP_STATUS_BAR_WIDGET_ID: String = "McpServerStatusBarWidget"
