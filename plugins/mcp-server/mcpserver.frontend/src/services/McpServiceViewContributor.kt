// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.frontend.services

import com.intellij.execution.services.ServiceEventListener
import com.intellij.execution.services.ServiceViewContributor
import com.intellij.execution.services.ServiceViewDescriptor
import com.intellij.execution.services.ServiceViewNonActivatingDescriptor
import com.intellij.execution.services.ServiceViewToolWindowDescriptor
import com.intellij.execution.services.SimpleServiceViewDescriptor
import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.frontend.toolwindow.McpConfigurationPanel
import com.intellij.mcpserver.frontend.toolwindow.McpToolCallsPanel
import com.intellij.mcpserver.toolwindow.McpDiagnosticService
import com.intellij.mcpserver.toolwindow.McpSessionInfo
import com.intellij.mcpserver.toolwindow.TransportType
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.application
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Timer

internal class McpServiceViewContributor : ServiceViewContributor<McpSessionInfo> {
  override fun getViewDescriptor(project: Project): ServiceViewDescriptor {
    project.service<McpServiceViewHelperService>()
    return McpServerRootDescriptor(project)
  }

  override fun getServices(project: Project): List<McpSessionInfo> {
    return service<McpDiagnosticService>().getSessions()
  }

  override fun getServiceDescriptor(project: Project, service: McpSessionInfo): ServiceViewDescriptor {
    return project.service<McpServiceViewHelperService>().getSessionDescriptor(service)
  }
}

private class McpServerRootDescriptor(
  private val project: Project,
) : SimpleServiceViewDescriptor(McpServerBundle.message("mcp.service.view.root.name"), AllIcons.Nodes.McpServerWidget),
    ServiceViewNonActivatingDescriptor,
    ServiceViewToolWindowDescriptor {
  private var contentComponent: JComponent? = null

  override fun getToolWindowId(): String = McpServerBundle.message("mcp.service.view.root.name")

  override fun getToolWindowIcon(): Icon = AllIcons.Toolwindows.ToolWindowServices

  override fun getStripeTitle(): String = McpServerBundle.message("mcp.service.view.root.name")

  override fun isExclusionAllowed(): Boolean = false

  override fun getContentComponent(): JComponent {
    return contentComponent ?: createContentComponent().also { contentComponent = it }
  }

  private fun createContentComponent(): JComponent {
    val tabbedPane = JBTabbedPane()
    tabbedPane.addTab(McpServerBundle.message("mcp.toolwindow.tab.tool.calls"),
                      project.service<McpServiceViewHelperService>().toolCallsPanel)
    tabbedPane.addTab(McpServerBundle.message("mcp.toolwindow.tab.configuration"), McpConfigurationPanel(project))
    val panel = JPanel(BorderLayout())
    panel.add(tabbedPane, BorderLayout.CENTER)
    return panel
  }
}

private class McpSessionDescriptor(val session: McpSessionInfo) : ServiceViewDescriptor {
  private var contentComponent: JComponent? = null

  val contentDisposable: Disposable = Disposer.newDisposable("MCP session view content")

  override fun getPresentation(): ItemPresentation {
    val clientName = session.clientInfo?.name ?: "..." //NON-NLS
    val transport = session.transportType.displayName()
    val version = session.clientInfo?.version
    return PresentationData("$clientName ($transport)", version, AllIcons.Nodes.McpServerWidget, null) //NON-NLS
  }

  override fun getContentComponent(): JComponent {
    return contentComponent ?: createContentComponent().also { contentComponent = it }
  }

  private fun createContentComponent(): JComponent {
    val diagnosticService = service<McpDiagnosticService>()
    val tabbedPane = JBTabbedPane()
    Disposer.register(session, contentDisposable)

    val durationLabel = javax.swing.JLabel(StringUtil.formatDuration(System.currentTimeMillis() - session.startTimeMs))
    val timer = Timer(1000) {
      durationLabel.text = StringUtil.formatDuration(System.currentTimeMillis() - session.startTimeMs)
    }
    Disposer.register(contentDisposable) {
      timer.stop()
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
    tabbedPane.addTab(McpServerBundle.message("mcp.toolwindow.tab.session"), infoPanel)

    val toolCallsPanel = McpToolCallsPanel(diagnosticService, session.sessionId)
    Disposer.register(contentDisposable, toolCallsPanel)
    tabbedPane.addTab(McpServerBundle.message("mcp.toolwindow.tab.tool.calls"), toolCallsPanel)

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

@Service(Service.Level.PROJECT)
private class McpServiceViewHelperService : Disposable {
  // Every reset of the service view model asks the contributor for new descriptors.
  val toolCallsPanel: McpToolCallsPanel by lazy {
    McpToolCallsPanel(service<McpDiagnosticService>()).also { Disposer.register(this, it) }
  }
  private val sessionDescriptors = ConcurrentHashMap<String, McpSessionDescriptor>()

  init {
    service<McpDiagnosticService>().observeSessions(this) { sessions ->
      sessionDescriptors.values.removeIf { it.session !in sessions }
      fireServiceViewReset()
    }
  }

  // A restarted session keeps its id, but its old info disposed the content of the old descriptor.
  fun getSessionDescriptor(session: McpSessionInfo): McpSessionDescriptor {
    return sessionDescriptors.compute(session.sessionId) { _, cached ->
      cached?.takeIf { it.session === session } ?: McpSessionDescriptor(session)
    }!!
  }

  override fun dispose() {
    sessionDescriptors.values.forEach { Disposer.dispose(it.contentDisposable) }
  }

  private fun fireServiceViewReset() {
    application.messageBus
      .syncPublisher(ServiceEventListener.TOPIC)
      .handle(ServiceEventListener.ServiceEvent.createResetEvent(McpServiceViewContributor::class.java))
  }
}