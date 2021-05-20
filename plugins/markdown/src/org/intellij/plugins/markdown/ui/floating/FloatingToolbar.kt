// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.floating

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.ui.LightweightHint
import com.intellij.ui.awt.RelativePoint
import java.awt.Point

internal class FloatingToolbar(val editor: Editor) {
  companion object {
    private const val verticalGap = 2
  }

  private val selectionListener = SelectionListener()
  private val mouseMotionListener = MouseMotionListener()

  private var hint: LightweightHint? = null


  init {
    registerListeners()
  }


  fun isShown() = hint != null

  fun hideIfShown() { hint?.hide() }


  fun showIfHidden() {
    if (hint != null) return

    val leftGroup = ActionManager.getInstance().getAction("Markdown.Toolbar.Floating") as ActionGroup
    val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, leftGroup, true)
    toolbar.setTargetComponent(editor.contentComponent)
    toolbar.setReservePlaceAutoPopupIcon(false)

    val newHint = LightweightHint(toolbar.component)
    newHint.setForceShowAsPopup(true)

    HintManagerImpl.getInstanceImpl().showEditorHint(
      newHint, editor,
      getHintPosition(newHint),
      HintManager.HIDE_BY_ESCAPE or HintManager.UPDATE_BY_SCROLLING,
      0, true
    )
    newHint.addHintListener { this.hint = null }
    this.hint = newHint
  }


  fun updateLocationIfShown() {
    val hint = hint ?: return // for smart casts

    val layeredPane = editor.contentComponent.rootPane.layeredPane
    val hintPos = getHintPosition(hint)
    hint.setLocation(RelativePoint(layeredPane, hintPos))
  }


  internal fun registerListeners() {
    editor.selectionModel.addSelectionListener(selectionListener)
    editor.addEditorMouseMotionListener(mouseMotionListener)
  }


  internal fun unregisterListeners() {
    editor.selectionModel.removeSelectionListener(selectionListener)
    editor.removeEditorMouseMotionListener(mouseMotionListener)
  }


  private fun getHintPosition(hint: LightweightHint): Point {
    val hintPos = HintManagerImpl.getInstanceImpl().getHintPosition(hint, editor, HintManager.DEFAULT)
    // because of `hint.setForceShowAsPopup(true)`, HintManager.ABOVE does not place the hint above
    // the hint remains on the line, so we need to move it up ourselves
    hintPos.translate(0, -(hint.component.preferredSize.height + verticalGap))
    return hintPos
  }


  private inner class SelectionListener : com.intellij.openapi.editor.event.SelectionListener {
    override fun selectionChanged(e: SelectionEvent) {
      if (e.newRange.length == 0) {
        hideIfShown()
        return
      }

      if (isShown())
        updateLocationIfShown()
      else showIfHidden()
    }
  }


  private inner class MouseMotionListener : EditorMouseMotionListener {
    override fun mouseMoved(e: EditorMouseEvent) {
      val visualPosition = e.visualPosition

      val hoverSelected = editor.caretModel.allCarets.any {
        val beforeSelectionEnd = it.selectionEndPosition.after(visualPosition)
        val afterSelectionStart = visualPosition.after(it.selectionStartPosition)
        beforeSelectionEnd && afterSelectionStart
      }

      if (hoverSelected)
        showIfHidden()
    }
  }
}