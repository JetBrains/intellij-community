package com.intellij.mcpserver.frontend.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.intellij.icons.AllIcons
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.clients.McpClientInfo
import com.intellij.mcpserver.frontend.widget.McpServerStatusBarWidget.Companion.POPUP_WIDTH
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.platform.compose.swing.components.ActionLink
import com.intellij.platform.compose.swing.components.Comment
import com.intellij.ui.components.DropDownLink
import com.intellij.ui.dsl.builder.IntelliJSpacingConfiguration
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.button.RadioButton
import org.jetbrains.compose.swing.components.layout.Alignment
import org.jetbrains.compose.swing.components.layout.Arrangement
import org.jetbrains.compose.swing.components.layout.Column
import org.jetbrains.compose.swing.components.layout.ColumnScope
import org.jetbrains.compose.swing.components.layout.Glue
import org.jetbrains.compose.swing.components.layout.Row
import org.jetbrains.compose.swing.components.layout.RigidArea
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.emptyBorder
import org.jetbrains.compose.swing.modifier.appearance.font
import org.jetbrains.compose.swing.modifier.appearance.foreground
import org.jetbrains.compose.swing.modifier.appearance.icon
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.toolTip
import org.jetbrains.compose.swing.modifier.interaction.defaultButton
import org.jetbrains.compose.swing.modifier.interaction.enabled
import org.jetbrains.compose.swing.modifier.interaction.initialFocus
import org.jetbrains.compose.swing.modifier.layout.maximumSize
import org.jetbrains.compose.swing.modifier.listener.componentListener
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.tooling.Preview

private val spacing = IntelliJSpacingConfiguration()

private enum class ClientButtonState { Normal, Configuring, Configured }

//private const val ANIMATION_DURATION_MS = 150
private const val MAX_SHOWN_CLIENTS = 3

//private fun <T> animationSpec() = tween<T>(durationMillis = ANIMATION_DURATION_MS)

//private val fadeTransition = fadeIn(animationSpec = animationSpec()) togetherWith fadeOut(animationSpec = animationSpec())
//private val rowEnterTransition = expandVertically(animationSpec = animationSpec())
//private val rowExitTransition = shrinkVertically(animationSpec = animationSpec())

private fun SwingModifier.popupPadding(): SwingModifier = with(spacing.dialogUnscaledGaps) {
  emptyBorder(JBUI.scale(top), JBUI.scale(left), JBUI.scale(bottom), JBUI.scale(right))
}

@Composable
internal fun McpServerPopupContent(model: McpServerPopupModel, modifier: SwingModifier = SwingModifier) {
  var isEnabled by remember(model) { mutableStateOf(model.initialEnabled) }
  var isConsentRequired by remember(model) { mutableStateOf(false) }

  Column(modifier.background(JBUI.CurrentTheme.Popup.BACKGROUND).opaque(true)) {
    if (isConsentRequired) {
      ConsentContent(
        modifier = SwingModifier.fillWidth().popupPadding(),
        helpLink = model.helpLink,
        onConfirm = {
          model.enable()
          isEnabled = true
          isConsentRequired = false
        },
        onCancel = { isConsentRequired = false },
      )
    }
    else {
      HeaderRow(
        modifier = SwingModifier.fillWidth().popupPadding(),
        onSettingsClick = model::onSettingsClick,
        isServerEnabled = isEnabled,
        onEnabledChange = { enable ->
          if (enable) isConsentRequired = true
          else {
            model.disable()
            isEnabled = false
          }
        },
        connectionCount = model.activeConnectionCount,
        onShowConnectionsClick = model::showInServiceView,
      )
      if (isEnabled) EnabledMcpSettings(SwingModifier.fillWidth().popupPadding(), model)
      else DisabledDescription(SwingModifier.fillWidth().popupPadding(), model)
    }
    FooterRow(SwingModifier.fillWidth().background(UIUtil.getPanelBackground()).opaque(true).popupPadding(), model, isEnabled)
  }
}

@Composable
private fun ConsentContent(modifier: SwingModifier, helpLink: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
  Column(modifier.opaque(false), verticalArrangement = Arrangement.spacedBy(JBUI.scale(spacing.verticalMediumGap))) {
    Label(McpServerBundle.message("dialog.title.mcp.server.consent"),
          SwingModifier.icon(AllIcons.General.Warning).font(JBFont.h4()))
    PopupText(McpServerBundle.message("dialog.message.mcp.server.consent", helpLink), SwingModifier.fillWidth())
    Row(SwingModifier.fillWidth().opaque(false), horizontalArrangement = Arrangement.End) {
      Button(McpServerBundle.message("dialog.mcp.server.consent.cancel.button"), onClick = onCancel, modifier = SwingModifier.opaque(false))
      RigidArea(width = JBUI.scale(spacing.horizontalSmallGap), height = 0)
      Button(McpServerBundle.message("dialog.mcp.server.consent.enable.button"),
             onClick = onConfirm,
             modifier = SwingModifier.defaultButton().initialFocus().opaque(false))
    }
  }
}

@Composable
private fun ColumnScope.EnabledMcpSettings(modifier: SwingModifier, model: McpServerPopupModel) {
  var isBraveMode by remember(model) { mutableStateOf(model.braveMode) }
  val clients by produceState(emptyList(), model) { value = model.detectClients() }
  Column(SwingModifier.fillWidth().opaque(false)) {

    PopupDivider()

    if (clients.isNotEmpty()) {
      DetectedClientList(modifier, clients, model)
      PopupDivider()
    }
    Row(
      modifier = modifier.opaque(false),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(JBUI.scale(spacing.horizontalSmallGap)),
    ) {
      CheckBox(
        text = McpServerBundle.message("mcp.server.popup.brave.mode"),
        checked = isBraveMode,
        modifier = SwingModifier.opaque(false),
        onCheckedChange = { newValue ->
          model.setBraveMode(newValue)
          isBraveMode = newValue
        },
      )
      Label("", SwingModifier.icon(AllIcons.General.Note).toolTip(McpServerBundle.message("mcp.server.popup.brave.mode.description")))
    }
  }
}

@Composable
private fun HeaderRow(
  modifier: SwingModifier,
  onSettingsClick: () -> Unit,
  isServerEnabled: Boolean,
  onEnabledChange: (Boolean) -> Unit,
  connectionCount: Int,
  onShowConnectionsClick: () -> Unit,
) {
  Row(modifier.opaque(false), verticalAlignment = Alignment.CenterVertically) {
    CheckBox(
      text = McpServerBundle.message("mcp.server.configurable.name"),
      checked = isServerEnabled,
      onCheckedChange = onEnabledChange, modifier = SwingModifier.opaque(false),
    )
    Glue(modifier = SwingModifier.weight(1f))
    if (isServerEnabled) ActionLink(
      text = McpServerBundle.message("mcp.server.status.bar.popup.active.connections", connectionCount),
      onClick = onShowConnectionsClick,
      modifier = SwingModifier.emptyBorder(left = spacing.horizontalSmallGap, right = spacing.horizontalSmallGap, top = 0, bottom = 0),
    )
    ActionLink(
      text = "",
      modifier = SwingModifier.icon(AllIcons.General.Settings).toolTip(McpServerBundle.message("configurable.name.mcp.plugin")),
      onClick = onSettingsClick,
    )
  }
}

@Composable
private fun FooterRow(modifier: SwingModifier, model: McpServerPopupModel, isEnabled: Boolean) {
  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    if (isEnabled) CopyConfigLink(model)
    Glue(modifier = SwingModifier.weight(1f))
    ActionLink(McpServerBundle.message("mcp.server.status.bar.popup.all.mcp.tools"), model::onToolsSettingsClick)
  }
}

@Composable
private fun DisabledDescription(modifier: SwingModifier, model: McpServerPopupModel) {
  @NlsSafe
  val clientNames by produceState(emptyList(), model) { value = model.detectClientNames() }
  Column(modifier.opaque(false), verticalArrangement = Arrangement.spacedBy(JBUI.scale(spacing.verticalMediumGap))) {
    PopupText(
      text = HtmlChunk.text(McpServerBundle.message("mcp.server.status.bar.popup.description")).toString(),
      modifier = SwingModifier.fillWidth().font(JBFont.medium()),
    )
    if (clientNames.isNotEmpty()) {
      @Suppress("HardCodedStringLiteral")
      val names: @NlsSafe String = clientNames.joinToString(" • ")
      PopupText(
        text = HtmlChunk.text(McpServerBundle.message("mcp.server.status.bar.popup.clients.hint")).toString() + "<br>" +
               HtmlChunk.text(names),
        modifier = SwingModifier.fillWidth().foreground(UIUtil.getContextHelpForeground()).font(JBFont.small()),
      )
    }
  }
}

@Composable
private fun CopyConfigLink(model: McpServerPopupModel) {
  var feedbackSucceeded by remember { mutableStateOf<Boolean?>(null) }
  val scope = rememberCoroutineScope()
  val copyWithFeedback = rememberUpdatedState<(() -> Boolean) -> Unit> { copy ->
    scope.launch {
      feedbackSucceeded = copy()
      delay(1.seconds)
      feedbackSucceeded = null
    }
  }
  val success = feedbackSucceeded
  if (success != null) {
    Label(
      text = McpServerBundle.message(if (success) "mcp.server.popup.copy.config" else "mcp.server.popup.copy.config.failed"),
      modifier = SwingModifier.foreground(if (success) UIUtil.getContextHelpForeground() else JBUI.CurrentTheme.Label.errorForeground()),
    )
  }
  else {
    SwingNode(factory = {
      DropDownLink(McpServerBundle.message("copy.mcp.server.configuration")) {
        val entries = buildList {
          add(CopyEntry(McpServerBundle.message("copy.config.sse"), model::copySseConfig,
                        McpServerBundle.message("mcp.server.popup.copy.config.menu.json")))
          add(CopyEntry(McpServerBundle.message("copy.config.stdio"), model::copyStdioConfig))
          add(CopyEntry(McpServerBundle.message("copy.config.stream"), model::copyStreamConfig))
          val urlsHeading = McpServerBundle.message("mcp.server.popup.copy.config.menu.url")
          model.sseUrl?.let { add(CopyEntry(it, model::copySseUrl, urlsHeading)) }
          model.streamUrl?.let { add(CopyEntry(it, model::copyStreamUrl, if (model.sseUrl == null) urlsHeading else null)) }
        }
        JBPopupFactory.getInstance().createListPopup(object : BaseListPopupStep<CopyEntry>(null, entries) {
          override fun getTextFor(value: CopyEntry): String = value.text
          override fun getSeparatorAbove(value: CopyEntry): ListSeparator? = value.heading?.let { ListSeparator(it) }
          override fun onChosen(selectedValue: CopyEntry, finalChoice: Boolean): PopupStep<*>? = doFinalStep {
            copyWithFeedback.value(selectedValue.copy)
          }
        })
      }
    })
  }
}

private class CopyEntry(val text: @NlsSafe String, val copy: () -> Boolean, val heading: @Nls String? = null)

@Composable
private fun DetectedClientList(modifier: SwingModifier, clients: List<DetectedClientInfo>, model: McpServerPopupModel) {
  val scope = rememberCoroutineScope()
  val pendingClientIds = remember(clients) {
    mutableStateSetOf<String>().apply { addAll(clients.filter { it.needsConfig }.map { it.id }) }
  }
  val clientButtonStates = remember(clients) { mutableStateMapOf<String, ClientButtonState>() }
  val clientErrors = remember(clients) { mutableStateMapOf<String, String>() }
  val pendingClients = clients.filter { it.id in pendingClientIds }

  when {
    clients.isEmpty() -> Label(
      text = McpServerBundle.message("mcp.server.popup.clients.empty"),
      modifier = modifier.foreground(UIUtil.getContextHelpForeground()),
    )
    pendingClients.isEmpty() /*all configured*/ -> Label(
      text = McpServerBundle.message("mcp.server.popup.clients.all.configured", clients.size),
      modifier = SwingModifier
        .icon(AllIcons.Status.Success)
        .foreground(UIUtil.getContextHelpForeground())
        .emptyBorder(JBUI.insets(spacing.verticalMediumGap, spacing.horizontalDefaultGap)),
    )
    else -> Column(modifier.opaque(false), verticalArrangement = Arrangement.spacedBy(JBUI.scale(spacing.verticalSmallGap))) {
      pendingClients.take(MAX_SHOWN_CLIENTS).forEach { client ->
        key(client.id) {
          val buttonState = clientButtonStates[client.id] ?: ClientButtonState.Normal
          val currentError = clientErrors[client.id] ?: client.initialError
          Column(
            modifier = SwingModifier.fillWidth().opaque(false),
            verticalArrangement = Arrangement.spacedBy(JBUI.scale(spacing.verticalComponentGap)),
          ) {
            Row(
              modifier = SwingModifier.fillWidth().opaque(false),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Label(client.displayName)
              if (buttonState == ClientButtonState.Configured) Label(
                text = McpServerBundle.message("mcp.server.configured"),
                modifier = SwingModifier.icon(AllIcons.Status.Success)
                  .emptyBorder(
                    top = JBUI.scale(spacing.verticalComponentGap + 3), left = JBUI.scale(spacing.horizontalDefaultGap),
                    bottom = JBUI.scale(spacing.verticalComponentGap + 2), right = JBUI.scale(spacing.horizontalDefaultGap),
                  ),
              )
              else Button(
                text = McpServerBundle.message("autoconfigure.mcp.server"),
                modifier = SwingModifier.enabled(buttonState == ClientButtonState.Normal).opaque(false),
                onClick = {
                  scope.launch {
                    clientButtonStates[client.id] = ClientButtonState.Configuring
                    if (model.configureClient(client.id)) {
                      clientButtonStates[client.id] = ClientButtonState.Configured
                      delay(1.seconds)
                      pendingClientIds.remove(client.id)
                    }
                    else {
                      clientButtonStates.remove(client.id)
                      clientErrors[client.id] = McpServerBundle.message("mcp.server.popup.client.config.failed")
                    }
                  }
                },
              )
            }
            if (currentError != null) Row(
              modifier = SwingModifier.fillWidth().opaque(false),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(JBUI.scale(spacing.horizontalSmallGap)),
            ) {
              Label("", SwingModifier.icon(AllIcons.General.Error))
              PopupText(
                text = HtmlChunk.text(currentError).toString(),
                modifier = SwingModifier.weight(1f).foreground(JBUI.CurrentTheme.Label.errorForeground()),
              )
            }
          }
        }
      }
      val hiddenClientCount = pendingClients.size - MAX_SHOWN_CLIENTS
      if (hiddenClientCount > 0) {
        ActionLink(McpServerBundle.message("mcp.server.popup.clients.show.more", hiddenClientCount), model::onSettingsClick)
      }
    }
  }
}

@Composable
private fun PopupText(text: @NlsContexts.DetailedDescription String, modifier: SwingModifier = SwingModifier) {
  Comment(text, SwingModifier.font(JBFont.label()).foreground(JBUI.CurrentTheme.Label.foreground()) then modifier)
}

@Composable
private fun ColumnScope.PopupDivider(modifier: SwingModifier = SwingModifier) {
  Column(modifier.fillWidth().background(JBUI.CurrentTheme.Popup.separatorColor()).opaque(true)) {
    RigidArea(0, 1)
  }
}

@Composable
@Preview(widthPx = 450)
@Suppress("unused")
fun McpPanelPreview() {
  var previewResetKey by remember { mutableStateOf(0) }
  var previewConfigurationSucceeds by remember { mutableStateOf(true) }

  class PreviewMcpServerPopupModel : McpServerPopupModel {
    override val initialEnabled = true
    override val braveMode = false
    override val sseUrl = "http://127.0.0.1:64342/sse"
    override val streamUrl = "http://127.0.0.1:64342/stream"
    override suspend fun detectClientNames() = McpClientInfo.Name.entries.map { it.baseName }
    override suspend fun detectClients(): List<DetectedClientInfo> = listOf(
      DetectedClientInfo("VS_CODE", "Visual Studio Code", needsConfig = true, initialError = null),
      DetectedClientInfo("CURSOR", "Cursor", true, initialError = McpServerBundle.message("mcp.server.configured.port.mismatch")),
      DetectedClientInfo("CLAUDE_APP", "Claude App", needsConfig = true, initialError = null),
      DetectedClientInfo("WINDSURF", "Windsurf", needsConfig = true, initialError = null),
      DetectedClientInfo("JUNIE", "Junie", needsConfig = true, initialError = null),
    )

    override val helpLink = "https://www.jetbrains.com/help/idea/mcp-server.html#supported-tools"
    override val activeConnectionCount = 3

    override fun enable() {}
    override fun disable() {}
    override fun setBraveMode(value: Boolean) {}
    override suspend fun configureClient(id: String) = previewConfigurationSucceeds
    override fun copySseConfig() = true
    override fun copyStdioConfig() = false
    override fun copyStreamConfig() = true
    override fun copySseUrl() = true
    override fun copyStreamUrl() = true
    override fun onSettingsClick() {}
    override fun onToolsSettingsClick() {}
    override fun showInServiceView() {}
  }

  var sizeText by remember { mutableStateOf("") }
  Column {
    Label(sizeText, SwingModifier.emptyBorder(8))
    key(previewResetKey) {
      McpServerPopupContent(
        model = remember { PreviewMcpServerPopupModel() },
        modifier = SwingModifier
          .fillWidth()
          .maximumSize(JBUI.scale(POPUP_WIDTH), Int.MAX_VALUE)
          .componentListener { event ->
            val size = event.component.size
            sizeText = "${size.width} x ${size.height} px, ${JBUI.unscale(size.width)} x ${JBUI.unscale(size.height)} unscaled"
          },
      )
    }
    Column(SwingModifier.emptyBorder(spacing.horizontalDefaultGap), Arrangement.spacedBy(spacing.horizontalSmallGap)) {
      Label("On configure click:")
      RadioButton("Configure successfully", previewConfigurationSucceeds, { previewConfigurationSucceeds = true })
      RadioButton("Show error", !previewConfigurationSucceeds, { previewConfigurationSucceeds = false })
      Button("Reset clients", onClick = { previewResetKey++ })
    }
  }
}
