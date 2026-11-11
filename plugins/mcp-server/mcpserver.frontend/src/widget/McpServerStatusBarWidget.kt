package com.intellij.mcpserver.frontend.widget

import com.intellij.icons.AllIcons
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.frontend.settings.McpServerSettingsConfigurable
import com.intellij.mcpserver.frontend.settings.McpToolFilterConfigurable
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.platform.compose.swing.AnchorAlignmentPositionProvider
import com.intellij.platform.compose.swing.createComposeSwingPopup
import com.intellij.ui.BadgeIconSupplier
import com.intellij.ui.popup.PopupState
import com.intellij.util.Consumer
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.width
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JComponent

internal class McpServerStatusBarWidget(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) : StatusBarWidget, StatusBarWidget.IconPresentation {
  private val popupState = PopupState.forPopup()
  private var statusBar: StatusBar? = null

  internal companion object {
    private val MCP_LOGO: Icon = AllIcons.Nodes.McpServerWidget
    private val BADGE_ICON_SUPPLIER = BadgeIconSupplier(MCP_LOGO)
    const val POPUP_WIDTH: Int = 450
  }

  override fun ID(): String = MCP_WIDGET_ID

  override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

  override fun install(statusBar: StatusBar) {
    this.statusBar = statusBar
    if (McpServerService.getInstanceIfCreated() == null) { // do not init McpServerService on EDT
      coroutineScope.launch { // update icon once available
        serviceAsync<McpServerService>()
        withContext(Dispatchers.EDT) {
          updatePresentation()
        }
      }
    }
  }

  @RequiresEdt
  fun updatePresentation() {
    ThreadingAssertions.softAssertAwtOperationsThread()

    statusBar?.updateWidget(ID())
  }

  override fun getIcon(): Icon {
    val service = McpServerService.getInstanceIfCreated() // do not init McpServerService on EDT
                  ?: return BADGE_ICON_SUPPLIER.originalIcon

    return if (service.isRunning) BADGE_ICON_SUPPLIER.successIcon
    else BADGE_ICON_SUPPLIER.errorIcon
  }

  @Nls
  override fun getTooltipText(): String {
    val service = McpServerService.getInstanceIfCreated() // do not init McpServerService on EDT
    if (service == null) return McpServerBundle.message("mcp.server.status.bar.widget.tooltip.starting")

    return if (service.isRunning) McpServerBundle.message("mcp.server.status.bar.widget.tooltip.enabled")
    else McpServerBundle.message("mcp.server.status.bar.widget.tooltip.disabled")
  }

  override fun getClickConsumer(): Consumer<MouseEvent> = Consumer { event ->
    val component = event.component as? JComponent ?: return@Consumer
    if (!popupState.isRecentlyHidden) {
      createAndShowPopup(component)
    }
  }

  private fun createAndShowPopup(component: JComponent) {
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
    val popup = JBPopupFactory.getInstance().createComposeSwingPopup(
      // The popup sits above the widget, and the end edges of the two are in line.
      positionProvider = AnchorAlignmentPositionProvider(anchorAlignment = Alignment.TopEnd, popupAlignment = Alignment.BottomEnd),
      configure = {
        setCancelOnOtherWindowOpen(true)
        setCancelOnClickOutside(true)
        setShowBorder(false)
        setFocusable(true)
        setRequestFocus(true)
      },
    ) {
      McpServerPopupContent(model = model, modifier = SwingModifier.width(JBUI.scale(POPUP_WIDTH)))
    }
    Disposer.register(this, popup)

    popupState.prepareToShow(popup)
    popup.show(component)
  }

  override fun dispose() {
    statusBar = null
    coroutineScope.cancel()
  }
}
