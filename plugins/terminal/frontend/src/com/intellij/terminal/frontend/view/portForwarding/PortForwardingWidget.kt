// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.portForwarding

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.terminal.frontend.view.portForwarding.actions.ForwardPortAction
import com.intellij.terminal.frontend.view.portForwarding.actions.ForwardPortAndOpenInBrowserAction
import com.intellij.terminal.frontend.view.portForwarding.actions.OpenForwardedPortInBrowserAction
import com.intellij.terminal.frontend.view.portForwarding.actions.StopForwardingAction
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.DropDownLink
import com.intellij.ui.components.panels.ListLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.WrapLayout
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.block.ui.TerminalUi
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.time.Duration.Companion.milliseconds

/**
 * UI component listing the currently detected ports for one terminal session.
 *
 * The visual structure contains of:
 * 1. A leading "Your application is listening on ports:" label
 * 2. Wrapping container of [PortForwardingComponent]s, one per detected port.
 *
 * The panel collapses to zero size when no ports are known, so it effectively disappears.
 */
internal class PortForwardingWidget(
  private val model: PortForwardingViewModel,
  coroutineScope: CoroutineScope,
) : BorderLayoutPanel(), UiDataProvider {
  private val portsContainer: JPanel = JPanel().also {
    it.layout = WrapLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(4))
    it.isOpaque = false
  }

  init {
    border = JBUI.Borders.compound(
      JBUI.Borders.customLine(TerminalUi.promptSeparatorColor(), 0, 0, 1, 0),
      JBUI.Borders.empty(5, 13)
    )
    isOpaque = false
    layoutComponents()

    coroutineScope.launch(Dispatchers.UI + ModalityState.any().asContextElement()) {
      @OptIn(FlowPreview::class)
      model.items
        .debounce(300.milliseconds)
        .collect { items ->
          renderPorts(items)
        }
    }
  }

  private fun layoutComponents() {
    val label = JLabel(TerminalBundle.message("port.forwarding.application.is.listening")).also {
      it.border = JBUI.Borders.emptyRight(2)
    }
    addToLeft(label)
    addToCenter(portsContainer)
  }

  private fun renderPorts(items: List<PortForwardingItem>) {
    portsContainer.removeAll()
    for (item in items.sortedBy { it.remotePort }) {
      portsContainer.add(PortForwardingComponent(item))
    }
    revalidate()
    repaint()
  }

  override fun getPreferredSize(): Dimension {
    return if (model.items.value.isEmpty()) Dimension(0, 0) else super.getPreferredSize()
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[PortForwardingViewModel.KEY] = model
  }
}

/**
 * One row in the [PortForwardingWidget] — represents a single [PortForwardingItem].
 *
 * - For [PortForwardingItem.NotForwarded] the row is a single [DropDownLink] showing the
 *   remote port number. Clicking it opens the "unforwarded" action group.
 * - For [PortForwardingItem.Forwarded] the row contains of:
 *   a non-clickable remote-port link, a "forwarded to" label,
 *   and a [DropDownLink] for the local port that opens the "forwarded" action group.
 */
private class PortForwardingComponent(
  private val item: PortForwardingItem,
) : JPanel(ListLayout.horizontal(JBUI.scale(4))), UiDataProvider {

  init {
    isOpaque = false
    when (item) {
      is PortForwardingItem.Forwarded -> layoutForwarded(item)
      is PortForwardingItem.NotForwarded -> layoutNotForwarded(item)
    }
  }

  private fun layoutForwarded(item: PortForwardingItem.Forwarded) {
    add(createRemotePortLink(item))
    add(createForwardedToLabel())
    add(createLocalPortDropDown(item))
  }

  private fun layoutNotForwarded(item: PortForwardingItem.NotForwarded) {
    add(createRemotePortDropDown(item))
  }

  /** Non-interactive link styled to match the dropdown's text — purely visual. */
  private fun createRemotePortLink(item: PortForwardingItem.Forwarded): JComponent {
    return ActionLink(item.remotePort.toString())
  }

  private fun createForwardedToLabel(): JComponent {
    return JLabel(TerminalBundle.message("port.forwarding.forwarded.to.label"))
  }

  private fun createLocalPortDropDown(item: PortForwardingItem.Forwarded): JComponent {
    return DropDownLink(item.localPort.toString()) { link ->
      openActionGroupPopup(link, createForwardedPortActionGroup())
    }
  }

  private fun createRemotePortDropDown(item: PortForwardingItem.NotForwarded): JComponent {
    return DropDownLink(item.remotePort.toString()) { link ->
      openActionGroupPopup(link, createUnforwardedPortActionGroup())
    }
  }

  private fun openActionGroupPopup(target: Component, group: ActionGroup): JBPopup {
    return JBPopupFactory.getInstance().createActionGroupPopup(
      null,
      group,
      DataManager.getInstance().getDataContext(target),
      JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
      true,
    )
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[PortForwardingItem.KEY] = item
  }
}

private fun createUnforwardedPortActionGroup(): ActionGroup = DefaultActionGroup(
  ForwardPortAction(),
  ForwardPortAndOpenInBrowserAction(),
)

private fun createForwardedPortActionGroup(): ActionGroup = DefaultActionGroup(
  StopForwardingAction(),
  OpenForwardedPortInBrowserAction(),
)