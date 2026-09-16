package com.intellij.mcpserver.frontend.widget

import androidx.compose.runtime.Stable
import com.intellij.execution.services.ServiceViewManager
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.clients.McpClient
import com.intellij.mcpserver.createSseServerJsonEntry
import com.intellij.mcpserver.createStdioMcpServerJsonConfiguration
import com.intellij.mcpserver.createStreamableServerJsonEntry
import com.intellij.mcpserver.frontend.services.McpServiceViewContributor
import com.intellij.mcpserver.impl.McpClientDetector
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.impl.util.network.McpServerConnectionAddressProvider
import com.intellij.mcpserver.settings.McpServerSettings
import com.intellij.mcpserver.toolwindow.McpDiagnosticService
import com.intellij.mcpserver.util.getHelpLink
import com.intellij.mcpserver.util.getPathForMcp
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.ui.TextTransferable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Stable
internal interface McpServerPopupModel {
  val initialEnabled: Boolean
  val braveMode: Boolean
  val sseUrl: String?
  val streamUrl: String?
  val helpLink: String
  suspend fun detectClientNames(): List<String>
  suspend fun detectClients(): List<DetectedClientInfo>
  val activeConnectionCount: Int

  fun enable()
  fun disable()
  fun setBraveMode(value: Boolean)
  suspend fun configureClient(id: String): Boolean
  fun copySseConfig(): Boolean
  fun copyStdioConfig(): Boolean
  fun copyStreamConfig(): Boolean
  fun copySseUrl(): Boolean
  fun copyStreamUrl(): Boolean
  fun onSettingsClick()
  fun onToolsSettingsClick()
  fun showInServiceView()
}

private val LOG = logger<McpServerPopupModelImpl>()

/** Describes a detected client for the popup client list. */
@Stable
internal data class DetectedClientInfo(
  val id: String,
  val displayName: String,
  val needsConfig: Boolean,
  val initialError: String?,
)

internal class McpServerPopupModelImpl(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
  private val onSettingsClickAction: () -> Unit,
  private val onToolsSettingsClickAction: () -> Unit,
  private val onStateChangedAction: () -> Unit,
) : McpServerPopupModel {
  private val addressProvider = McpServerConnectionAddressProvider.getInstanceOrNull()

  private fun copyToClipboard(text: String): Boolean = try {
    CopyPasteManager.getInstance().setContents(TextTransferable(text as CharSequence))
    true
  }
  catch (e: Exception) {
    LOG.error("Failed to copy MCP configuration to clipboard", e)
    false
  }

  override val initialEnabled: Boolean get() = McpServerSettings.getInstance().enableMcpServer
  override val braveMode: Boolean get() = McpServerSettings.getInstance().enableBraveMode
  override val sseUrl: String? get() = if (McpServerService.getInstance().isRunning) addressProvider?.serverSseUrl else null
  override val streamUrl: String? get() = if (McpServerService.getInstance().isRunning) addressProvider?.serverStreamUrl else null

  override val activeConnectionCount: Int get() = service<McpDiagnosticService>().activeSessionCount

  override val helpLink: String get() = getHelpLink("mcp-server.html#supported-tools")

  override suspend fun detectClientNames(): List<String> = withContext(Dispatchers.IO) {
    McpClientDetector.detectGlobalMcpClients().map { it.mcpClientInfo.displayName }
  }

  override suspend fun detectClients(): List<DetectedClientInfo> = withContext(Dispatchers.IO) {
    McpClientDetector.detectGlobalMcpClients().map { client ->
      val needsConfig = !client.isConnectedToThisIde()
      val initialError = if (!client.isPortCorrect()) {
        McpServerBundle.message("mcp.server.configured.port.mismatch")
      }
      else null
      DetectedClientInfo(
        id = client.mcpClientInfo.name.toString(),
        displayName = client.mcpClientInfo.displayName,
        needsConfig = needsConfig,
        initialError = initialError,
      )
    }
  }

  override suspend fun configureClient(id: String): Boolean = withContext(Dispatchers.IO) {
    try {
      val client = McpClientDetector.detectGlobalMcpClients()
        .find { it.mcpClientInfo.name.toString() == id } ?: return@withContext false
      client.autoConfigure()
      true
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      LOG.info("Failed to configure client: ${e.message}", e)
      false
    }
  }

  override fun enable() {
    coroutineScope.launch {
      McpServerSettings.getInstance().enableMcpServer = true
      McpServerService.getInstance().settingsChanged(true)
      withContext(Dispatchers.EDT) {
        onStateChangedAction()
      }
    }
  }

  override fun disable() {
    coroutineScope.launch {
      McpServerSettings.getInstance().enableMcpServer = false
      McpServerService.getInstance().settingsChanged(false)
      withContext(Dispatchers.EDT) {
        onStateChangedAction()
      }
    }
  }

  override fun setBraveMode(value: Boolean) {
    McpServerSettings.getInstance().enableBraveMode = value
  }

  override fun copySseConfig(): Boolean =
    copyToClipboard(McpClient.json.encodeToString(createSseServerJsonEntry(McpServerService.getInstance().port, project.getPathForMcp())))

  override fun copyStdioConfig(): Boolean =
    copyToClipboard(McpClient.json.encodeToString(createStdioMcpServerJsonConfiguration(McpServerService.getInstance().port, project.getPathForMcp())))

  override fun copyStreamConfig(): Boolean =
    copyToClipboard(McpClient.json.encodeToString(createStreamableServerJsonEntry(McpServerService.getInstance().port, project.getPathForMcp())))

  override fun copySseUrl(): Boolean = sseUrl?.let { copyToClipboard(it) } ?: false

  override fun copyStreamUrl(): Boolean = streamUrl?.let { copyToClipboard(it) } ?: false


  override fun onSettingsClick() {
    onSettingsClickAction()
  }

  override fun onToolsSettingsClick() {
    onToolsSettingsClickAction()
  }

  override fun showInServiceView() {
    val toolWindowId = ServiceViewManager.getInstance(project).getToolWindowId(McpServiceViewContributor::class.java)
    ToolWindowManager.getInstance(project).getToolWindow(toolWindowId)?.activate(null)
  }
}
