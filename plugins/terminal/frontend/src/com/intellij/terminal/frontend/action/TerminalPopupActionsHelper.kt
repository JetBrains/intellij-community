package com.intellij.terminal.frontend.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionHolder
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBUI
import java.awt.Point
import java.awt.event.MouseEvent

internal fun AnActionEvent.getPreferredPopupPoint(): RelativePoint? {
  val inputEvent = this.inputEvent
  if (inputEvent is MouseEvent) {
    val comp = inputEvent.component
    if (comp is AnActionHolder) {
      return RelativePoint(comp.parent, Point(comp.x + JBUI.scale(3), comp.y + comp.height + JBUI.scale(3)))
    }
  }
  return null
}

internal fun AnActionEvent.showPopup(popup: JBPopup) {
  val popupPoint = this.getPreferredPopupPoint()
  if (popupPoint != null) {
    popup.show(popupPoint)
  }
  else {
    popup.showInFocusCenter()
  }
}
