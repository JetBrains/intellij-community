// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.actionSystem.ActionPopupMenu
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.ex.ActionPopupMenuListener
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.WindowManager
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.toolWindow.ToolWindowHeader
import com.intellij.ui.GotItTooltip
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.UiNotifyConnector
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.jetbrains.plugins.terminal.action.EnableBlockTerminalUiAction
import org.jetbrains.plugins.terminal.fus.TerminalUsageTriggerCollector
import java.awt.Component
import java.awt.Point
import javax.swing.SwingUtilities

internal object BlockTerminalPromotionService {
  @RequiresEdt
  fun showPromotionOnce(project: Project, widget: TerminalWidget) {
    UiNotifyConnector.doWhenFirstShown(widget.component, { doShowPromotionOnce(project, widget) }, widget)
  }

  private fun doShowPromotionOnce(project: Project, widget: TerminalWidget) {
    val text = TerminalBundle.message("new.terminal.promotion.text")
    val tooltip = GotItTooltip("new.terminal.promotion", text)
    if (!tooltip.canShow()) {
      return  // it was shown already once
    }

    val optionsButton = findUiComponent(project) { button: ActionButton ->
      val isOptionsAction = button.action is ToolWindowHeader.ShowOptionsAction
      val isFromTerminalToolWindow = InternalDecoratorImpl.findNearestDecorator(button)?.toolWindowId == TerminalToolWindowFactory.TOOL_WINDOW_ID
      isOptionsAction && isFromTerminalToolWindow
    } ?: return

    Disposer.register(widget, tooltip)
    // Mark it as shown, to not show it again, not depending on the way how it was closed
    Disposer.register(tooltip) { tooltip.gotIt() }

    // Close the tooltip if the Terminal tool window options menu is opened
    ActionManagerEx.getInstanceEx().addActionPopupMenuListener(object : ActionPopupMenuListener {
      override fun actionPopupMenuCreated(menu: ActionPopupMenu) {
        if (menu.actionGroup.getChildren(null).any { it is EnableBlockTerminalUiAction }) {
          Disposer.dispose(tooltip)
        }
      }
    }, tooltip)

    tooltip.withHeader(TerminalBundle.message("new.terminal.promotion.text.header"))
      .withGotItButtonAction { TerminalUsageTriggerCollector.triggerPromotionGotItClicked(project) }

    tooltip.show(optionsButton) { button, balloon ->
      val window = SwingUtilities.getWindowAncestor(button)
      val buttonBounds = SwingUtilities.convertRectangle(button, button.bounds, window)
      val balloonPrefSize = balloon.preferredSize
      // Check that balloon can fit below the button. If not, show it on the left.
      if (buttonBounds.y + buttonBounds.height + balloonPrefSize.height <= window.height) {
        Point(button.width / 2, button.height)
      }
      else Point(0, button.height / 2)
    }
    TerminalUsageTriggerCollector.triggerPromotionShown(project)
  }

  private inline fun <reified T : Component> findUiComponent(project: Project, predicate: (T) -> Boolean): T? {
    val root = WindowManager.getInstance().getFrame(project) ?: return null
    findUiComponent(root, predicate)?.let { return it }
    for (window in root.ownedWindows) {
      findUiComponent(window, predicate)?.let { return it }
    }
    return null
  }

  private inline fun <reified T : Component> findUiComponent(root: Component, predicate: (T) -> Boolean): T? {
    val component = UIUtil.uiTraverser(root).find {
      it is T && it.isVisible && it.isShowing && predicate(it)
    }
    return component as? T
  }
}