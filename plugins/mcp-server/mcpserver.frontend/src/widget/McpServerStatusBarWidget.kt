package com.intellij.mcpserver.frontend.widget

import com.intellij.icons.AllIcons
import com.intellij.ide.setToolTipText
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.frontend.settings.McpServerSettingsConfigurable
import com.intellij.mcpserver.frontend.settings.McpToolFilterConfigurable
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.platform.compose.swing.composeSwingPanel
import com.intellij.ui.BadgeIconSupplier
import com.intellij.ui.components.IconLabelButton
import com.intellij.ui.popup.PopupState
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.Point
import javax.swing.Icon
import javax.swing.JComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.modifier.layout.maximumSize
import org.jetbrains.compose.swing.modifier.layout.minimumSize
import org.jetbrains.compose.swing.modifier.listener.componentListener
import java.awt.BorderLayout

internal class McpServerStatusBarWidget(private val project: Project) : CustomStatusBarWidget {
  private val popupState = PopupState.forPopup()

  companion object {
    private val MCP_LOGO: Icon = AllIcons.Nodes.McpServerWidget
    private val BADGE_ICON_SUPPLIER = BadgeIconSupplier(MCP_LOGO)
    const val POPUP_WIDTH: Int = 450
    private const val POPUP_HEIGHT = 180 //approx size of the content
  }

  private val widgetComponent by lazy { createComponent() }

  @Suppress("RAW_SCOPE_CREATION")
  private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  private fun createComponent(): IconLabelButton {
    return IconLabelButton(getCurrentIcon()) {
      if (!popupState.isRecentlyHidden) {
        createAndShowPopup(widgetComponent)
      }
    }.also {
      it.setToolTipText(HtmlChunk.text(getCurrentTooltip()))
    }
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun updatePresentation() {
    ThreadingAssertions.softAssertAwtOperationsThread()

    widgetComponent.icon = getCurrentIcon()
    widgetComponent.setToolTipText(HtmlChunk.text(getCurrentTooltip()))
  }

  private fun getCurrentIcon(): Icon {
    val service = McpServerService.getInstanceIfCreated() // do not init McpServerService on EDT
    if (service == null) {
      coroutineScope.launch { // update icon once available, if not yet
        serviceAsync<McpServerService>()
        withContext(Dispatchers.EDT) {
          updatePresentation()
        }
      }

      return BADGE_ICON_SUPPLIER.originalIcon
    }

    return if (service.isRunning) BADGE_ICON_SUPPLIER.successIcon
    else BADGE_ICON_SUPPLIER.errorIcon
  }

  @Nls
  private fun getCurrentTooltip(): String {
    val service = McpServerService.getInstanceIfCreated() // do not init McpServerService on EDT
    if (service == null) return McpServerBundle.message("mcp.server.status.bar.widget.tooltip.starting")

    return if (service.isRunning) McpServerBundle.message("mcp.server.status.bar.widget.tooltip.enabled")
    else McpServerBundle.message("mcp.server.status.bar.widget.tooltip.disabled")
  }

  private fun createAndShowPopup(component: JComponent) {
    var popupRef: JBPopup? = null
    val model = McpServerPopupModelImpl(
      project = project,
      coroutineScope = coroutineScope,
      onSettingsClickAction = {
        popupState.popup?.cancel()
        ShowSettingsUtil.getInstance().showSettingsDialog(project, McpServerSettingsConfigurable::class.java)
      },
      onToolsSettingsClickAction = {
        popupState.popup?.cancel()
        ShowSettingsUtil.getInstance().showSettingsDialog(project, McpToolFilterConfigurable::class.java)
      },
      onStateChangedAction = { updatePresentation() },
    )
    val contentDisposable = Disposer.newDisposable("MCP popup")
    Disposer.register(this, contentDisposable)
    val panel = composeSwingPanel(contentDisposable) {
      McpServerPopupContent(
        model = model,
        modifier = SwingModifier
          .layoutConstraint(BorderLayout.NORTH)
          .componentListener(onComponentResized = {
            popupRef?.let { popup ->
              popup.size = Dimension(POPUP_WIDTH, it.component.height)
              adjustPopupLocation(popup, component)
            }
          })
          .minimumSize(JBUI.scale(POPUP_WIDTH), 0)
          .maximumSize(JBUI.scale(POPUP_WIDTH), Int.MAX_VALUE),
      )
    }.apply {
      preferredSize = JBUI.size(POPUP_WIDTH, POPUP_HEIGHT)
    }

    val popup = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(panel, null)
      .setCancelOnOtherWindowOpen(true)
      .setCancelOnClickOutside(true)
      .setShowBorder(false)
      .setFocusable(true)
      .setRequestFocus(true)
      .createPopup()
    Disposer.register(this, popup)
    Disposer.register(popup, contentDisposable)
    popupRef = popup

    popupState.prepareToShow(popup)
    popup.showInCorner(component)
  }

  override fun getComponent(): JComponent = widgetComponent

  override fun ID(): String = McpServerStatusBarWidgetFactory.WIDGET_ID

  override fun install(statusBar: StatusBar) {}

  override fun dispose() {
    coroutineScope.cancel()
  }
}

private fun adjustPopupLocation(popup: JBPopup, component: JComponent) {
  val location = Point(component.locationOnScreen).apply {
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
