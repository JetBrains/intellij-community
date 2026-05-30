@file:Suppress("UnstableApiUsage", "FunctionName")

package com.intellij.mcpserver.widget

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.rememberComponentRectPositionProvider
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.clients.McpClientInfo
import com.intellij.ui.dsl.builder.IntelliJSpacingConfiguration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.ExternalLink
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.OutlinedSlimButton
import org.jetbrains.jewel.ui.component.Popup
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.popupContainerStyle
import org.jetbrains.jewel.ui.theme.tooltipStyle
import kotlin.time.Duration.Companion.seconds

private val spacing = IntelliJSpacingConfiguration()

@Composable
internal fun McpServerPopupContent(
  model: McpServerPopupModel,
  modifier: Modifier = Modifier,
) {
  var isEnabled by remember { mutableStateOf(model.initialEnabled) }
  val gaps = spacing.dialogUnscaledGaps

  Column(
    modifier = modifier
      .widthIn(min = McpServerStatusBarWidget.POPUP_WIDTH.dp)
      .background(JewelTheme.popupContainerStyle.colors.background)
      .animateContentSize()
      .padding(start = gaps.left.dp, end = gaps.right.dp, top = gaps.top.dp, bottom = gaps.bottom.dp)
  ) {
    HeaderRow(onSettingsClick = model::onSettingsClick)

    Spacer(Modifier.height(spacing.verticalSmallGap.dp))

    CheckboxRow(
      text = McpServerBundle.message("enable.mcp.server"),
      checked = isEnabled,
      onCheckedChange = { newValue ->
        if (newValue) {
          if (!model.tryEnable()) return@CheckboxRow
        }
        else {
          model.disable()
        }
        isEnabled = newValue
      },
    )

    Spacer(Modifier.height(spacing.verticalSmallGap.dp))

    if (isEnabled) EnabledMcpSettings(model)
    else DisabledDescription(helpLink = model.helpLink, clientNames = model.detectedClientNames, onBrowse = model::browseUrl)
  }
}

@Composable
private fun EnabledMcpSettings(model: McpServerPopupModel) {
  var isBraveMode by remember { mutableStateOf(model.braveMode) }

  Link(
    text = McpServerBundle.message("mcp.server.status.bar.popup.active.connections", model.activeConnectionCount),
    onClick = { model.showInServiceView() },
  )

  Spacer(Modifier.height(spacing.verticalSmallGap.dp))

  if (model.sseUrl != null || model.streamUrl != null) Row(horizontalArrangement = Arrangement.spacedBy(spacing.horizontalSmallGap.dp)) {
    model.sseUrl?.let { sseUrl -> ExternalLink(text = sseUrl, onClick = { model.browseUrl(sseUrl) }) }
    model.streamUrl?.let { streamUrl -> ExternalLink(text = streamUrl, onClick = { model.browseUrl(streamUrl) }) }
  }

  Spacer(Modifier.height(spacing.verticalMediumGap.dp))

  ManualClientConfigSection(
    onCopySse = { model.copySseConfig() },
    onCopyStdio = { model.copyStdioConfig() },
    onCopyStream = { model.copyStreamConfig() },
  )

  Spacer(Modifier.height(spacing.verticalMediumGap.dp))

  CheckboxRow(
    text = McpServerBundle.message("checkbox.enable.brave.mode.skip.command.execution.confirmations"),
    checked = isBraveMode,
    onCheckedChange = { newValue ->
      model.setBraveMode(newValue)
      isBraveMode = newValue
    },
  )

  Text(
    modifier = Modifier.padding(start = 30.dp, top = spacing.verticalComponentGap.dp),
    text = McpServerBundle.message("text.warning.enabling.brave.mode.will.allow.terminal.commands.to.execute.without.confirmation.use.with.caution"),
    color = JewelTheme.globalColors.text.info,
    fontSize = 12.sp,
  )

  model.unconfiguredMessage?.let { message ->
    UnconfiguredClientsPromotion(
      message = message,
      onConfigureClick = model::onSettingsClick,
    )
  }
}

@Composable
private fun HeaderRow(onSettingsClick: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(text = McpServerBundle.message("mcp.server.status.bar.popup.header"), fontWeight = FontWeight.Bold)
    IconButton(onClick = onSettingsClick) {
      Icon(AllIconsKeys.General.Settings, contentDescription = null)
    }
  }
}

@Composable
private fun DisabledDescription(helpLink: String, clientNames: List<String>, onBrowse: (String) -> Unit) {
  Column {
    Text(
      text = McpServerBundle.message("mcp.server.status.bar.popup.description"),
      color = JewelTheme.globalColors.text.info,
      fontSize = 12.sp,
    )
    Spacer(Modifier.height(spacing.verticalCommentBottomGap.dp))
    ExternalLink(text = McpServerBundle.message("mcp.server.status.bar.popup.all.mcp.tools"), onClick = { onBrowse(helpLink) })
    if (clientNames.isNotEmpty()) {
      Spacer(Modifier.height(spacing.verticalMediumGap.dp))
      Text(
        text = McpServerBundle.message("mcp.server.status.bar.popup.clients.hint"),
        color = JewelTheme.globalColors.text.info,
        fontSize = 12.sp,
      )
      Spacer(Modifier.height(spacing.verticalCommentBottomGap.dp))
      Text(text = clientNames.joinToString(" • "), fontSize = 12.sp)
    }
  }
}

@Composable
private fun ManualClientConfigSection(
  onCopySse: () -> Boolean,
  onCopyStdio: () -> Boolean,
  onCopyStream: () -> Boolean,
) {
  Column {
    Text(text = McpServerBundle.message("mcp.general.client"), fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(spacing.verticalCommentBottomGap.dp))
    Text(
      text = McpServerBundle.message("settings.comment.manual.config"),
      color = JewelTheme.globalColors.text.info,
      fontSize = 12.sp,
    )
    Spacer(Modifier.height(spacing.verticalComponentGap.dp))
    Row(
      horizontalArrangement = Arrangement.spacedBy(spacing.horizontalSmallGap.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(text = McpServerBundle.message("copy.config.label"))
      CopyConfigButton(McpServerBundle.message("copy.config.sse")) { onCopySse() }
      CopyConfigButton(McpServerBundle.message("copy.config.stdio")) { onCopyStdio() }
      CopyConfigButton(McpServerBundle.message("copy.config.stream")) { onCopyStream() }
    }
  }
}

@Composable
private fun CopyConfigButton(
  label: String,
  onClick: () -> Boolean,
) {
  var copyResult by remember { mutableStateOf<Boolean?>(null) }
  val transitionProgress = remember { Animatable(0f) }
  val scope = rememberCoroutineScope()

  Box(contentAlignment = Alignment.Center) {
    OutlinedSlimButton(
      onClick = {
        if (copyResult != null) return@OutlinedSlimButton

        copyResult = onClick()
        scope.launch {
          transitionProgress.snapTo(0f)
          transitionProgress.animateTo(1f, tween(500))
          delay(1.seconds)
          transitionProgress.animateTo(0f, tween(500))
          copyResult = null
        }
      },
      modifier = Modifier
        .alpha(1f - transitionProgress.value)
        .scale(1f - 0.15f * transitionProgress.value),
    ) {
      Text(label)
    }

    copyResult?.let { result ->
      Icon(
        key = if (result) AllIconsKeys.Actions.Checked else AllIconsKeys.General.Error,
        contentDescription = null,
        modifier = Modifier
          .alpha(transitionProgress.value)
          .scale(0.8f + 0.2f * transitionProgress.value),
      )

      val tooltipStyle = JewelTheme.tooltipStyle
      val shape = RoundedCornerShape(tooltipStyle.metrics.cornerSize)
      Popup(popupPositionProvider = rememberComponentRectPositionProvider(Alignment.TopCenter, Alignment.TopCenter)) {
        Box(
          modifier = Modifier
            .alpha(transitionProgress.value)
            .border(tooltipStyle.metrics.borderWidth, tooltipStyle.colors.border, shape)
            .background(color = tooltipStyle.colors.background, shape = shape)
            .padding(tooltipStyle.metrics.contentPadding)
        ) {
          Text(
            text = if (result) McpServerBundle.message("json.configuration.copied.to.clipboard")
            else McpServerBundle.message("json.configuration.copy.failed"),
            color = if (result) tooltipStyle.colors.content else JewelTheme.globalColors.text.error,
          )
        }
      }
    }
  }
}

@Composable
private fun UnconfiguredClientsPromotion(message: String, onConfigureClick: () -> Unit) {
  Spacer(Modifier.height(spacing.verticalMediumGap.dp))
  Column {
    Text(message)
    Spacer(Modifier.height(spacing.verticalComponentGap.dp))
    Link(text = McpServerBundle.message("mcp.unconfigured.clients.detected.configure.settings.json"), onClick = onConfigureClick)
  }
}

@Composable
@Preview
fun McpPanelPreview() {
  class PreviewMcpServerPopupModel : McpServerPopupModel {
    override val initialEnabled = true
    override val braveMode = false
    override val sseUrl = "http://127.0.0.1:64342/sse"
    override val streamUrl = "http://127.0.0.1:64342/stream"
    override val detectedClientNames = McpClientInfo.Name.entries.map { it.baseName }
    override val unconfiguredMessage = McpServerBundle.message(
      "mcp.unconfigured.clients.detected.notification.message",
      detectedClientNames.take(2).joinToString(", "),
    )
    override val helpLink = "https://www.jetbrains.com/help/idea/mcp-server.html#supported-tools"
    override val activeConnectionCount = 3

    override fun tryEnable() = true
    override fun disable() {}
    override fun setBraveMode(value: Boolean) {}
    override fun copySseConfig() = true
    override fun copyStdioConfig() = false
    override fun copyStreamConfig() = true
    override fun browseUrl(url: String) {}
    override fun onSettingsClick() {}
    override fun showInServiceView() {}
  }

  var sizeText by remember { mutableStateOf("") }
  val density = LocalDensity.current
  Box {
    McpServerPopupContent(
      model = PreviewMcpServerPopupModel(),
      modifier = Modifier.onSizeChanged { size ->
        val wDp = with(density) { size.width.toDp().value.toInt() }
        val hDp = with(density) { size.height.toDp().value.toInt() }
        sizeText = "$wDp x $hDp"
      },
    )
    Box(
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .background(Color.Black.copy(alpha = 0.5f))
        .padding(2.dp)
    ) {
      Text(sizeText, color = Color.White, fontSize = 8.sp)
    }
  }
}
