// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.frontend.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import com.intellij.icons.AllIcons
import com.intellij.ide.impl.ProjectUtil
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.clients.McpClient
import com.intellij.mcpserver.createSseServerJsonEntry
import com.intellij.mcpserver.createStdioMcpServerJsonConfiguration
import com.intellij.mcpserver.createStreamableServerJsonEntry
import com.intellij.mcpserver.frontend.util.getConsentDialog
import com.intellij.mcpserver.icons.McpserverIcons
import com.intellij.mcpserver.impl.McpClientDetector
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.impl.McpServerTerminalPromotionDismissalState
import com.intellij.mcpserver.settings.McpServerSettings
import com.intellij.mcpserver.util.getHelpLink
import com.intellij.mcpserver.util.getPathForMcp
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.compose.swing.ComposeSwingSearchableConfigurable
import com.intellij.platform.compose.swing.components.BrowserLink
import com.intellij.platform.compose.swing.components.Comment
import com.intellij.platform.compose.swing.components.OptionButton
import com.intellij.platform.compose.swing.components.FormGap
import com.intellij.platform.compose.swing.components.FormPanel
import com.intellij.platform.compose.swing.components.FormScope
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.JBColor
import com.intellij.util.io.createParentDirectories
import com.intellij.util.ui.ColorizeProxyIcon
import com.intellij.util.ui.TextTransferable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.icon
import org.jetbrains.compose.swing.modifier.appearance.toolTip
import org.jetbrains.compose.swing.modifier.layout.visible
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.io.path.createFile

/**
 * Settings page of the MCP Server plugin, rendered with the Swing-Compose UI stack.
 *
 * Persisted flags are wired with [bind] (Compose state is the source of UI truth; the base derives
 * `isModified`/`apply`/`reset`). The remaining display state — server status, detected clients — is
 * plain Compose state driven by the plugin services.
 */
class McpServerSettingsConfigurable : ComposeSwingSearchableConfigurable() {
  private val settings get() = McpServerSettings.getInstance()

  private var enabled by bind(settings::enableMcpServer)
  private var braveMode by bind(settings::enableBraveMode)
  private var showTerminalPromotion by bind(TerminalPromotionSetting::isShown, TerminalPromotionSetting::setShown)
  private var terminalAnsiHighlighting by bind(settings::enableTerminalAnsiHighlighting)

  private var serverRunning by mutableStateOf(false)
  private var sseUrl by mutableStateOf("")
  private var streamUrl by mutableStateOf("")
  private var globalClientRows by mutableStateOf<List<ClientRowState>>(emptyList())
  private var projectClients by mutableStateOf<ProjectClientsState?>(null)

  private val activeProject: Project? = ProjectUtil.getActiveProject()
  private val globalClientControllers = McpClientDetector.detectGlobalMcpClients()?.map { ClientController(it, project = null) } ?: emptyList()
  private var projectClientControllers = emptyList<ClientController>()
  private val disabledExplanationHtml: @NlsContexts.DetailedDescription String = buildDisabledExplanation()

  init {
    refreshServerStatus()
    globalClientRows = globalClientControllers.map { it.toRowState() }
  }

  override fun getDisplayName(): String = McpServerBundle.message("configurable.name.mcp.plugin")

  override fun getHelpTopic(): @NonNls String = "settings.mcp.server"

  override fun getId(): @NonNls String = "com.intellij.mcpserver.settings"

  @Composable
  override fun ComposeContent() {
    FormPanel {
      EnableStatusRow()

      // Hidden rather than left out while the server is on, because the row being there is what stops the
      // enable row's gap below collapsing into the first group's gap above, and so puts everything below it
      // 8px lower. The page this replaced hid it with visibleIf and stood that way.
      // TODO Ask the page's owners whether those 8px are meant. They only show while the server is on - the
      //  state in which the row they come from is invisible - which reads more like a side effect of
      //  visibleIf than a decision. If they are not, this goes back to being an `if` and the page moves up.
      FormRow { Comment(disabledExplanationHtml, modifier = SwingModifier.visible(!enabled)) }

      if (enabled) {
        projectClients?.let { ProjectClientsGroup(it) }
        ClientsGroup()
        ManualConfigGroup()
        CommandExecutionGroup()
      }

      TerminalGroup()
    }

    LaunchedEffect(Unit) { initialize() }
  }

  override fun reset() {
    super.reset()
    // Revert the live server state to the persisted value (the enable toggle starts/stops it eagerly).
    McpServerService.getInstance().settingsChanged(settings.enableMcpServer)
    refreshServerStatus()
  }

  override fun disposeUIResources() {
    // If the enable toggle was flipped for preview but never applied, revert the live server state.
    if (enabled != settings.enableMcpServer) {
      McpServerService.getInstance().settingsChanged(settings.enableMcpServer)
    }
    super.disposeUIResources()
  }

  @Composable
  private fun FormScope.EnableStatusRow() {
    FormRow(bottomGap = FormGap.SMALL) {
      // The label reads as a caption for the links that follow it, so they keep a small gap from it and
      // from each other rather than the gap that separates two unrelated controls.
      SmallGapAfter {
        CheckBox(
          text = if (serverRunning) McpServerBundle.message("enable.mcp.server.when.enabled")
          else McpServerBundle.message("enable.mcp.server"),
          checked = enabled,
          onCheckedChange = ::requestEnabledChange,
        )
      }
      if (enabled) {
        SmallGapAfter { BrowserLink(text = sseUrl, url = sseUrl, modifier = SwingModifier.toolTip(sseUrl)) }
        BrowserLink(text = streamUrl, url = streamUrl, modifier = SwingModifier.toolTip(streamUrl))
      }
    }
  }

  @Composable
  private fun FormScope.ProjectClientsGroup(projectClients: ProjectClientsState) {
    FormGroup(McpServerBundle.message("settings.client.project.group", projectClients.projectName), indent = false) {
      FormComment(McpServerBundle.message("settings.client.project.setup.description"))
      FormIndent {
        projectClients.clients.forEach { key(it.displayName) { ClientRows(it) } }
      }
    }
  }

  @Composable
  private fun FormScope.ClientsGroup() {
    FormGroup(McpServerBundle.message("settings.client.group"), indent = false) {
      FormComment(McpServerBundle.message("settings.setup.description"))
      FormIndent {
        globalClientRows.forEach { key(it.displayName) { ClientRows(it) } }
      }
    }
  }

  @Composable
  private fun FormScope.ManualConfigGroup() {
    FormGroup(McpServerBundle.message("mcp.general.client"), indent = false) {
      FormComment(McpServerBundle.message("settings.comment.manual.config"))
      FormIndent {
        FormRow {
          Button(McpServerBundle.message("copy.mcp.server.sse.configuration")) {
            copyJsonToClipboard(createSseServerJsonEntry(McpServerService.getInstance().port, mcpPath()))
          }
          Button(McpServerBundle.message("copy.mcp.server.stdio.configuration")) {
            copyJsonToClipboard(createStdioMcpServerJsonConfiguration(McpServerService.getInstance().port, mcpPath()))
          }
          Button(McpServerBundle.message("copy.mcp.server.stream.configuration")) {
            copyJsonToClipboard(createStreamableServerJsonEntry(McpServerService.getInstance().port, mcpPath()))
          }
        }
      }
    }
  }

  @Composable
  private fun FormScope.CommandExecutionGroup() {
    FormGroup(McpServerBundle.message("border.title.commands.execution")) {
      FormRow(
        comment = McpServerBundle.message(
          "text.warning.enabling.brave.mode.will.allow.terminal.commands.to.execute.without.confirmation.use.with.caution"
        ),
      ) {
        CheckBox(
          text = McpServerBundle.message("checkbox.enable.brave.mode.skip.command.execution.confirmations"),
          checked = braveMode,
          onCheckedChange = { braveMode = it },
        )
      }
    }
  }

  @Composable
  private fun FormScope.TerminalGroup() {
    FormGroup(McpServerBundle.message("settings.terminal.group"), indent = false) {
      FormRow(comment = McpServerBundle.message("settings.terminal.promotion.comment", McpServerBundle.ideDisplayName())) {
        CheckBox(
          text = McpServerBundle.message("settings.terminal.promotion.show"),
          checked = showTerminalPromotion,
          onCheckedChange = { showTerminalPromotion = it },
        )
      }
      FormRow(comment = McpServerBundle.message("settings.terminal.ansi.highlighting.flag.comment")) {
        CheckBox(
          text = McpServerBundle.message("settings.terminal.ansi.highlighting.flag"),
          checked = terminalAnsiHighlighting,
          onCheckedChange = { terminalAnsiHighlighting = it },
        )
      }
    }
  }

  /** One client: its name on a row of its own, then the button that configures it and how it stands. */
  @Composable
  private fun FormScope.ClientRows(state: ClientRowState) {
    // Detecting the per-transport options touches client config, so it is loaded off the UI thread when the rows appear.
    val options by produceState(emptyList()) { value = state.loadOptions() }

    FormRow(topGap = FormGap.SMALL) { Label(state.displayName) }
    FormRow {
      OptionButton(
        text = McpServerBundle.message("autoconfigure.mcp.server"),
        options = options,
        addSeparator = false,
        onClick = state.onAutoConfigure,
      )
      state.statusIcon?.let { SmallGapAfter { Label(text = "", modifier = SwingModifier.icon(it)) } }
      if (state.statusText.isNotEmpty()) {
        if (state.statusIsComment) Comment(state.statusText) else Label(state.statusText)
      }
    }
  }

  private suspend fun initialize() {
    val project = activeProject ?: return
    val detected = withContext(Dispatchers.Default) { McpClientDetector.detectProjectMcpClients(project) }
    if (detected.isEmpty()) return
    projectClientControllers = detected.map { ClientController(it, project) }
    projectClients = ProjectClientsState(project.name, projectClientControllers.map { it.toRowState() })
  }

  private fun requestEnabledChange(requested: Boolean) = SwingUtilities.invokeLater {
    if (!ConsentValidator.isValidNewValue(requested, activeProject)) return@invokeLater
    enabled = requested
    McpServerService.getInstance().settingsChanged(requested)
    refreshServerStatus()
  }

  private fun refreshServerStatus() {
    val service = McpServerService.getInstance()
    serverRunning = service.isRunning
    sseUrl = if (serverRunning) service.serverSseUrl else ""
    streamUrl = if (serverRunning) service.serverStreamUrl else ""
  }

  private fun refreshClients() {
    globalClientRows = globalClientControllers.map { it.toRowState() }
    projectClients = projectClientControllers.takeIf { it.isNotEmpty() }
      ?.let { controllers -> projectClients?.copy(clients = controllers.map { it.toRowState() }) }
  }

  private fun buildDisabledExplanation(): @NlsContexts.DetailedDescription String =
    McpServerBundle.message(
      "settings.explanation.when.server.disabled",
      McpServerBundle.message("mcp.server.status.bar.popup.description"),
      getHelpLink("mcp-server.html#supported-tools"),
      McpServerBundle.message("mcp.server.status.bar.popup.all.mcp.tools"),
      McpServerBundle.message("mcp.server.status.bar.popup.clients.hint"),
      globalClientControllers.joinToString("<br/>") { " • " + it.client.mcpClientInfo.displayName },
    )

  private fun copyJsonToClipboard(json: JsonElement) {
    CopyPasteManager.getInstance().setContents(TextTransferable(McpClient.json.encodeToString(json) as CharSequence))
  }

  private fun mcpPath(): String? = activeProject?.getPathForMcp()

  /** Owns the mutable per-client status and derives an immutable [ClientRowState] for the view. */
  private inner class ClientController(val client: McpClient, val project: Project?) {
    private var isConfigured = client.isConfigured() ?: false
    private var isPortCorrect = client.isPortCorrect()
    private var autoConfigured = false
    private var errorMessage: String? = null

    fun toRowState(): ClientRowState {
      val status = status()
      return ClientRowState(
        displayName = client.mcpClientInfo.displayName,
        statusIcon = status.icon,
        statusText = status.text,
        statusIsComment = status.isComment,
        loadOptions = ::loadOptions,
        onAutoConfigure = { performConfiguration { client.autoConfigure() } },
      )
    }

    /** How the client stands, in the order the states override one another. */
    private fun status(): ClientStatus {
      val error = errorMessage
      if (error != null) {
        return ClientStatus.problem(McpServerBundle.message("mcp.server.client.autoconfig.error", error))
      }

      val transport = client.getTransportTypesDisplayString()
      val configured =
        if (transport != null) McpServerBundle.message("mcp.server.configured.with.transport", transport)
        else McpServerBundle.message("mcp.server.configured")

      return when {
        autoConfigured -> ClientStatus.configured(McpServerBundle.message("mcp.server.client.restart.info.settings", configured))
        isConfigured && isPortCorrect -> ClientStatus.configured(configured)
        isConfigured -> ClientStatus.problem(McpServerBundle.message("mcp.server.configured.port.invalid"))
        else -> ClientStatus(
          icon = ColorizeProxyIcon.Simple(McpserverIcons.Expui.StatusDisabled, JBColor.GRAY),
          text = McpServerBundle.message("mcp.server.not.configured"),
          isComment = true,
        )
      }
    }

    suspend fun loadOptions(): List<AnAction> =
      withContext(Dispatchers.Default) {
        buildList {
          runCatching { client.getStreamableHttpConfig() }.getOrNull()?.let { config ->
            add(action(McpServerBundle.message("configure.with.0.transport", "Streamable HTTP")) {
              performConfiguration { client.configure(config) }
            })
          }
          runCatching { client.getSSEConfig() }.getOrNull()?.let { config ->
            add(action(McpServerBundle.message("configure.with.0.transport", "SSE")) {
              performConfiguration { client.configure(config) }
            })
          }
          add(action(McpServerBundle.message("configure.with.0.transport", "Stdio")) {
            performConfiguration { client.configure(client.getStdioConfig()) }
          })
          add(action(McpServerBundle.message("open.settings.json")) { event ->
            openFileInEditor(client.configPath, event.project ?: project)
          })
          add(action(McpServerBundle.message("copy.mcp.server.configuration")) { event ->
            event.coroutineScope.launch {
              copyJsonToClipboard(buildJsonObject {
                put(McpClient.productSpecificServerKey(), McpClient.json.encodeToJsonElement(client.getPreferredConfig()))
              })
              showCopiedBallon(event)
            }
          })
        }
      }

    private fun performConfiguration(action: suspend () -> Unit) {
      runCatching {
        runWithModalProgressBlocking(
          ModalTaskOwner.guess(),
          McpServerBundle.message("autoconfigure.progress.title"),
          TaskCancellation.nonCancellable(),
        ) {
          action()
        }
      }.onFailure {
        thisLogger().info(it)
        errorMessage =
          (it as? McpClient.McpClientConfigurationException)?.message
          ?: McpServerBundle.message("mcp.server.client.autoconfig.unknown.error")
      }.onSuccess {
        errorMessage = null
        isConfigured = true
        isPortCorrect = true
        autoConfigured = true
      }
      refreshClients()
    }
  }
}

/**
 * Whether the terminal promotion is shown, which is what the settings page offers, said the other way round
 * from what is stored: the store records that the user dismissed it.
 */
@ApiStatus.Internal
object TerminalPromotionSetting {
  fun isShown(): Boolean = !McpServerTerminalPromotionDismissalState.isDismissed()

  fun setShown(shown: Boolean) {
    if (shown) McpServerTerminalPromotionDismissalState.showAgain() else McpServerTerminalPromotionDismissalState.dismiss()
  }
}

@Immutable
internal data class ProjectClientsState(
  val projectName: String,
  val clients: List<ClientRowState>,
)

@Immutable
internal data class ClientRowState(
  val displayName: String,
  val statusIcon: Icon?,
  @NlsContexts.DetailedDescription
  val statusText: String,
  val statusIsComment: Boolean,
  val loadOptions: suspend () -> List<AnAction>,
  val onAutoConfigure: () -> Unit,
)

/**
 * The icon and the line beside a client's button: what is wrong with a client is said as a comment, what is
 * right about it as ordinary text.
 */
private class ClientStatus(
  val icon: Icon,
  val text: @NlsContexts.DetailedDescription String,
  val isComment: Boolean,
) {
  companion object {
    fun configured(text: @NlsContexts.DetailedDescription String): ClientStatus =
      ClientStatus(McpserverIcons.Expui.StatusEnabled, text, isComment = false)

    fun problem(text: @NlsContexts.DetailedDescription String): ClientStatus =
      ClientStatus(AllIcons.General.Error, text, isComment = true)
  }
}

private fun action(text: @NlsActions.ActionText String, perform: (AnActionEvent) -> Unit): AnAction = object : AnAction(text) {
  override fun actionPerformed(e: AnActionEvent) {
    perform(e)
  }
}

private object ConsentValidator : CheckboxValidator {
  override fun isValidNewValue(isSelected: Boolean, project: Project?): Boolean =
    !isSelected || getConsentDialog(project)
}

private interface CheckboxValidator {
  fun isValidNewValue(isSelected: Boolean, project: Project?): Boolean
}

@ApiStatus.Internal
fun openFileInEditor(filePath: Path, project: Project?) {
  if (project == null) {
    return
  }
  val definitelyCreatedFile = if (!Files.exists(filePath)) {
    filePath.createParentDirectories().createFile()
  }
  else {
    filePath
  }
  val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(definitelyCreatedFile)
  virtualFile?.let { file ->
    FileEditorManager.getInstance(project).openFile(file, true)
  }
}

private fun showCopiedBallon(event: AnActionEvent) {
  JBPopupFactory.getInstance()
    .createHtmlTextBalloonBuilder(McpServerBundle.message("json.configuration.copied.to.clipboard"), null, null, null).createBalloon()
    .showInCenterOf(event.inputEvent?.source as JComponent)
}
