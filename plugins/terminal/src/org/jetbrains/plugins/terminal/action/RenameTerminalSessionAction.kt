// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.impl.content.BaseLabel
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.ui.content.Content
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import com.intellij.util.ui.UIUtil
import java.awt.Font
import java.awt.Point
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.Box
import javax.swing.JTextField

class RenameTerminalSessionAction : TerminalSessionContextMenuActionBase(), DumbAware {

  override fun actionPerformed(e: AnActionEvent, terminalToolWindow: ToolWindow, content: Content?) {
    val baseLabel = e.getData(PlatformDataKeys.CONTEXT_COMPONENT) as? BaseLabel
    val contextContent = baseLabel?.content ?: return
    showRenamePopup(baseLabel, contextContent)
  }

  private fun showRenamePopup(baseLabel: BaseLabel, content: Content) {
    val textField = JTextField(content.displayName)
    textField.selectAll()

    val label = JBLabel("Enter new session name:")
    label.font = UIUtil.getLabelFont().deriveFont(Font.BOLD)

    val panel = SwingHelper.newLeftAlignedVerticalPanel(label, Box.createVerticalStrut(JBUI.scale(2)), textField)
    panel.addFocusListener(object : FocusAdapter() {
      override fun focusGained(e: FocusEvent?) {
        IdeFocusManager.findInstance().requestFocus(textField, false)
      }
    })

    val balloon = JBPopupFactory.getInstance().createDialogBalloonBuilder(panel, null)
      .setShowCallout(true)
      .setCloseButtonEnabled(false)
      .setAnimationCycle(0)
      .setDisposable(content)
      .setHideOnKeyOutside(true)
      .setHideOnClickOutside(true)
      .setRequestFocus(true)
      .setBlockClicksThroughBalloon(true)
      .createBalloon()

    textField.addKeyListener(object : KeyAdapter() {
      override fun keyPressed(e: KeyEvent?) {
        if (e != null && e.keyCode == KeyEvent.VK_ENTER) {
          if (!Disposer.isDisposed(content)) {
            content.displayName = textField.text
          }
          balloon.hide()
        }
      }
    })

    balloon.show(RelativePoint(baseLabel, Point(baseLabel.width / 2, 0)), Balloon.Position.above)
  }
}
