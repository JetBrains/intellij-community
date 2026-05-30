// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.services

import com.intellij.execution.services.ServiceViewContributor
import com.intellij.execution.services.ServiceViewDescriptor
import com.intellij.execution.services.ServiceViewToolWindowDescriptor
import com.intellij.execution.services.SimpleServiceViewDescriptor
import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.toolwindow.McpConfigurationPanel
import com.intellij.mcpserver.toolwindow.McpDiagnosticService
import com.intellij.mcpserver.toolwindow.McpSessionInfo
import com.intellij.mcpserver.toolwindow.McpToolCallsPanel
import com.intellij.mcpserver.toolwindow.TransportType
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

internal class McpServiceViewContributor : ServiceViewContributor<McpSessionInfo> {
  override fun getViewDescriptor(project: Project): ServiceViewDescriptor {
    return McpServerRootDescriptor(project)
  }

  override fun getServices(project: Project): List<McpSessionInfo> {
    return service<McpDiagnosticService>().getSessions()
  }

  override fun getServiceDescriptor(project: Project, service: McpSessionInfo): ServiceViewDescriptor {
    return McpSessionDescriptor(service)
  }
}

private class McpServerRootDescriptor(
  private val project: Project,
) : SimpleServiceViewDescriptor(McpServerBundle.message("mcp.service.view.root.name"), AllIcons.Nodes.McpServerWidget),
    ServiceViewToolWindowDescriptor {

  override fun getToolWindowId(): String = McpServerBundle.message("mcp.service.view.root.name")

  override fun getToolWindowIcon(): Icon = AllIcons.Toolwindows.ToolWindowServices

  override fun getStripeTitle(): String = McpServerBundle.message("mcp.service.view.root.name")

  override fun isExclusionAllowed(): Boolean = false

  override fun getContentComponent(): JComponent {
    val diagnosticService = service<McpDiagnosticService>()
    val tabbedPane = com.intellij.ui.components.JBTabbedPane()
    tabbedPane.addTab(McpServerBundle.message("mcp.toolwindow.tab.tool.calls"), McpToolCallsPanel(diagnosticService))
    tabbedPane.addTab(McpServerBundle.message("mcp.toolwindow.tab.configuration"), McpConfigurationPanel(project))
    val panel = JPanel(BorderLayout())
    panel.add(tabbedPane, BorderLayout.CENTER)
    return panel
  }
}

private class McpSessionDescriptor(private val session: McpSessionInfo) : ServiceViewDescriptor {
  override fun getPresentation(): ItemPresentation {
    val clientName = session.clientInfo?.name ?: "..." //NON-NLS
    val transport = session.transportType.displayName()
    val version = session.clientInfo?.version
    return PresentationData("$clientName ($transport)", version, AllIcons.Nodes.McpServerWidget, null) //NON-NLS
  }

  override fun getContentComponent(): JComponent {
    val diagnosticService = service<McpDiagnosticService>()
    val tabbedPane = com.intellij.ui.components.JBTabbedPane()

    val durationLabel = javax.swing.JLabel(StringUtil.formatDuration(System.currentTimeMillis() - session.startTimeMs))
    val timer = javax.swing.Timer(1000) {
      durationLabel.text = StringUtil.formatDuration(System.currentTimeMillis() - session.startTimeMs)
    }
    timer.start()

    val infoPanel = JPanel(BorderLayout())
    infoPanel.add(panel {
      row(McpServerBundle.message("mcp.toolwindow.sessions.column.client")) {
        label(session.clientInfo?.name ?: "...") //NON-NLS
      }
      row(McpServerBundle.message("mcp.toolwindow.sessions.column.version")) {
        label(session.clientInfo?.version ?: "...") //NON-NLS
      }
      row(McpServerBundle.message("mcp.toolwindow.sessions.column.transport")) {
        label(session.transportType.displayName()) //NON-NLS
      }
      row(McpServerBundle.message("mcp.toolwindow.sessions.column.duration")) {
        cell(durationLabel)
      }
    }.apply { border = JBUI.Borders.empty(8) }, BorderLayout.NORTH)
    infoPanel.addHierarchyListener { e ->
      if (e.id == java.awt.event.HierarchyEvent.HIERARCHY_CHANGED && (e.changeFlags and java.awt.event.HierarchyEvent.SHOWING_CHANGED.toLong()) != 0L) {
        if (!infoPanel.isShowing) timer.stop()
      }
    }
    tabbedPane.addTab(McpServerBundle.message("mcp.toolwindow.tab.session"), infoPanel)

    tabbedPane.addTab(McpServerBundle.message("mcp.toolwindow.tab.tool.calls"), McpToolCallsPanel(diagnosticService, session.sessionId))

    val panel = JPanel(BorderLayout())
    panel.add(tabbedPane, BorderLayout.CENTER)
    return panel
  }
}

private fun TransportType.displayName(): String = when (this) {
  TransportType.SSE -> "SSE"
  TransportType.STREAMABLE_HTTP -> "HTTP Stream"
  TransportType.STDIO -> "Stdio"
}