package org.intellij.plugins.markdown.ui.actions.styling

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.ui.awt.RelativePoint
import java.awt.Point
import java.awt.event.MouseEvent

internal class CreateOrChangeListPopupAction: AnAction(), Toggleable {
  private val actualGroup = CreateOrChangeListActionGroup()

  override fun actionPerformed(event: AnActionEvent) {
    val editor = event.getRequiredData(CommonDataKeys.EDITOR)
    val toolbar = object: ActionToolbarImpl(event.place, actualGroup, true) {
      override fun addNotify() {
        super.addNotify()
        updateActionsImmediately(true)
      }
    }
    toolbar.setReservePlaceAutoPopupIcon(false)
    toolbar.targetComponent = editor.contentComponent
    val toolbarComponent = toolbar.component
    val inputEvent = event.inputEvent
    if (inputEvent is MouseEvent) {
      val targetComponent = inputEvent.component
      val point = Point(0, targetComponent.y + targetComponent.height)
      val relativePoint = RelativePoint(inputEvent.component, point)
      HintManagerImpl.getInstanceImpl().showHint(
        toolbarComponent,
        relativePoint,
        HintManager.HIDE_BY_ESCAPE or HintManager.UPDATE_BY_SCROLLING,
        0
      )
    }
  }

  override fun update(event: AnActionEvent) {
    val children = actualGroup.getChildren(event).asSequence().filterIsInstance<ToggleAction>()
    val active = children.find { it.isSelected(event) }
    if (active == null) {
      val default = children.firstOrNull() ?: return
      event.presentation.text = default.templateText
      event.presentation.icon = default.templatePresentation.icon
      default.update(event)
      return
    }
    event.presentation.text = active.templateText
    event.presentation.icon = active.templatePresentation.icon
    active.update(event)
  }
}
