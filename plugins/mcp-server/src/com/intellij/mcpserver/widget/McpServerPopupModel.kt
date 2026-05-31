package com.intellij.mcpserver.widget

import androidx.compose.runtime.Stable
import com.intellij.execution.services.ServiceViewManager
import com.intellij.ide.BrowserUtil
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.clients.McpClient
import com.intellij.mcpserver.createSseServerJsonEntry
import com.intellij.mcpserver.createStdioMcpServerJsonConfiguration
import com.intellij.mcpserver.createStreamableServerJsonEntry
import com.intellij.mcpserver.impl.McpClientDetector
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.impl.util.network.McpServerConnectionAddressProvider
import com.intellij.mcpserver.services.McpServiceViewContributor
import com.intellij.mcpserver.settings.McpServerSettings
import com.intellij.mcpserver.toolwindow.McpDiagnosticService
import com.intellij.mcpserver.util.getConsentDialog
import com.intellij.mcpserver.util.getHelpLink
import com.intellij.openapi.components.service
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.ui.TextTransferable


@Stable
internal interface McpServerPopupModel {
  val initialEnabled: Boolean
  val braveMode: Boolean
  val sseUrl: String?
  val streamUrl: String?
  val unconfiguredMessage: String?
  val helpLink: String
  val detectedClientNames: List<String>
  val activeConnectionCount: Int

  fun tryEnable(): Boolean
  fun disable()
  fun setBraveMode(value: Boolean)
  fun copySseConfig(): Boolean
  fun copyStdioConfig(): Boolean
  fun copyStreamConfig(): Boolean
  fun browseUrl(url: String)
  fun onSettingsClick()
  fun showInServiceView()
}

internal class McpServerPopupModelImpl(
  private val project: Project,
  private val onSettingsClickAction: () -> Unit,
  private val onStateChangedAction: () -> Unit,
) : McpServerPopupModel {
  companion object {
    private val LOG = com.intellij.openapi.diagnostic.logger<McpServerPopupModelImpl>()
  }

  private val settings = McpServerSettings.getInstance()
  private val service = McpServerService.getInstance()
  private val addressProvider = McpServerConnectionAddressProvider.getInstanceOrNull()

  private fun copyToClipboard(text: String): Boolean = try {
    CopyPasteManager.getInstance().setContents(TextTransferable(text as CharSequence))
    true
  }
  catch (e: Exception) {
    LOG.error("Failed to copy MCP configuration to clipboard", e)
    false
  }

  override val initialEnabled: Boolean get() = settings.state.enableMcpServer
  override val braveMode: Boolean get() = settings.state.enableBraveMode
  override val sseUrl: String? get() = if (service.isRunning) addressProvider?.serverSseUrl else null
  override val streamUrl: String? get() = if (service.isRunning) addressProvider?.serverStreamUrl else null

  override val activeConnectionCount: Int get() = service<McpDiagnosticService>().activeSessionCount

  override val helpLink: String get() = getHelpLink("mcp-server.html#supported-tools")

  override val detectedClientNames: List<String> by lazy {
    McpClientDetector.detectGlobalMcpClients().map { it.mcpClientInfo.displayName }
  }

  override val unconfiguredMessage: String? by lazy {
    val unconfigured = McpClientDetector.detectGlobalMcpClients()
      .filter { it.isConfigured() != true || !it.isPortCorrect() }
      .map { it.mcpClientInfo.displayName }
    if (unconfigured.isNotEmpty()) {
      McpServerBundle.message("mcp.unconfigured.clients.detected.notification.message", unconfigured.joinToString(", "))
    }
    else null
  }

  override fun tryEnable(): Boolean {
    val consented = getConsentDialog(project)
    if (!consented) return false
    settings.state.enableMcpServer = true
    service.settingsChanged(true)
    onStateChangedAction()
    return true
  }

  override fun disable() {
    settings.state.enableMcpServer = false
    service.settingsChanged(false)
    onStateChangedAction()
  }

  override fun setBraveMode(value: Boolean) {
    settings.state.enableBraveMode = value
  }

  override fun copySseConfig(): Boolean =
    copyToClipboard(McpClient.json.encodeToString(createSseServerJsonEntry(service.port, project.basePath)))

  override fun copyStdioConfig(): Boolean =
    copyToClipboard(McpClient.json.encodeToString(createStdioMcpServerJsonConfiguration(service.port, project.basePath)))

  override fun copyStreamConfig(): Boolean =
    copyToClipboard(McpClient.json.encodeToString(createStreamableServerJsonEntry(service.port, project.basePath)))

  override fun browseUrl(url: String) {
    BrowserUtil.browse(url)
  }

  override fun onSettingsClick() {
    onSettingsClickAction()
  }

  override fun showInServiceView() {
    val toolWindowId = ServiceViewManager.getInstance(project).getToolWindowId(McpServiceViewContributor::class.java)
    ToolWindowManager.getInstance(project).getToolWindow(toolWindowId)?.activate(null)
  }
}
