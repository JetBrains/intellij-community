package com.intellij.mcpserver.widget

import com.intellij.ide.setToolTipText
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.settings.McpServerSettingsConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.BadgeIconSupplier
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import androidx.compose.runtime.remember
import com.intellij.icons.AllIcons
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.IconLabelButton
import com.intellij.ui.popup.PopupState
import com.intellij.ui.scale.JBUIScale
import org.jetbrains.jewel.bridge.JewelComposePanel
import java.awt.Dimension
import java.awt.Point
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.SwingUtilities

internal class McpServerStatusBarWidget(private val project: Project) : CustomStatusBarWidget {
  private val popupState = PopupState.forPopup()

  companion object {
    private val MCP_LOGO: Icon = AllIcons.Nodes.McpServerWidget
    private val BADGE_ICON_SUPPLIER = BadgeIconSupplier(MCP_LOGO)
    const val POPUP_WIDTH: Int = 450
    private const val POPUP_HEIGHT = 180 //approx size of the content
  }

  private val widgetComponent by lazy { createComponent() }

  private fun createComponent(): IconLabelButton {
    return IconLabelButton(getCurrentIcon()) {
      if (!popupState.isRecentlyHidden) {
        createAndShowPopup(widgetComponent)
      }
    }.also {
      it.setToolTipText(HtmlChunk.text(getCurrentTooltip()))
    }
  }

  fun updatePresentation() {
    widgetComponent.icon = getCurrentIcon()
    widgetComponent.setToolTipText(HtmlChunk.text(getCurrentTooltip()))
  }

  private fun getCurrentIcon(): Icon =
    if (McpServerService.getInstance().isRunning) MCP_LOGO
    else BADGE_ICON_SUPPLIER.errorIcon

  @org.jetbrains.annotations.Nls
  private fun getCurrentTooltip(): String =
    if (McpServerService.getInstance().isRunning) McpServerBundle.message("mcp.server.status.bar.widget.tooltip.enabled")
    else McpServerBundle.message("mcp.server.status.bar.widget.tooltip.disabled")

  private fun createAndShowPopup(component: JComponent) {
    var popupRef: JBPopup? = null
    var panelRef: JComponent? = null
    var lastHeight = 0

    val panel = JewelComposePanel(focusOnClickInside = true, config = {
      preferredSize = Dimension(POPUP_WIDTH, POPUP_HEIGHT)
    }) {
      val model = remember {
        McpServerPopupModelImpl(
          project = project,
          onSettingsClickAction = {
            popupState.popup?.cancel()
            ShowSettingsUtil.getInstance().showSettingsDialog(project, McpServerSettingsConfigurable::class.java)
          },
          onStateChangedAction = { updatePresentation() },
        )
      }
      McpServerPopupContent(
        model = model,
        onContentSizeChanged = { widthPx, heightPx ->
          if (heightPx == lastHeight) return@McpServerPopupContent
          lastHeight = heightPx
          SwingUtilities.invokeLater {
            val density = JBUIScale.sysScale(panelRef)
            val widthDp = (widthPx / density).toInt()
            val heightDp = (heightPx / density).toInt()
            panelRef?.preferredSize = Dimension(widthDp, heightDp)
            popupRef?.let { popup ->
              popup.size = Dimension(widthDp, heightDp)
              adjustPopupLocation(popup, component)
            }
          }
        },
      )
    }
    panelRef = panel

    val popup = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(panel, null)
      .setCancelOnOtherWindowOpen(true)
      .setCancelOnClickOutside(true)
      .setShowBorder(false)
      .setFocusable(true)
      .setRequestFocus(true)
      .createPopup()
    popupRef = popup

    popupState.prepareToShow(popup)
    popup.showInCorner(component)
  }

  override fun getComponent(): JComponent = widgetComponent

  override fun ID(): String = McpServerStatusBarWidgetFactory.WIDGET_ID

  override fun install(statusBar: StatusBar) {}

  override fun dispose() {}
}

private fun adjustPopupLocation(popup: JBPopup, component: JComponent) {
  val point = RelativePoint(component, Point())
  val location = Point(point.screenPoint).apply {
    x -= popup.size.width - component.width
    y -= popup.size.height
  }
  popup.setLocation(location)
}

private fun JBPopup.showInCorner(component: JComponent) {
  addListener(object : JBPopupListener {
    override fun beforeShown(event: LightweightWindowEvent) {
      adjustPopupLocation(event.asPopup(), component)
    }
  })
  show(component)
}
