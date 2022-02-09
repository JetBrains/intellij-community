// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.floating

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiEditorUtil
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parents
import com.intellij.ui.LightweightHint
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile
import org.jetbrains.annotations.ApiStatus
import java.awt.Point
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JComponent
import kotlin.properties.Delegates

@ApiStatus.Internal
open class FloatingToolbar(val editor: Editor, private val actionGroupId: String) : Disposable {
  private val mouseListener = MouseListener()
  private val keyboardListener = KeyboardListener()
  private val mouseMotionListener = MouseMotionListener()

  private var hint: LightweightHint? = null
  private var buttonSize: Int by Delegates.notNull()
  private var lastSelection: String? = null

  init {
    registerListeners()
  }

  fun isShown() = hint != null

  fun hideIfShown() {
    hint?.hide()
  }

  fun showIfHidden() {
    if (hint != null || !canBeShownAtCurrentSelection()) {
      return
    }
    val toolbar = createActionToolbar(editor.contentComponent) ?: return
    buttonSize = toolbar.maxButtonHeight

    val newHint = LightweightHint(toolbar.component)
    newHint.setForceShowAsPopup(true)

    showOrUpdateLocation(newHint)
    newHint.addHintListener { this.hint = null }
    this.hint = newHint
  }

  fun updateLocationIfShown() {
    showOrUpdateLocation(hint ?: return)
  }

  override fun dispose() {
    unregisterListeners()
    hideIfShown()
    hint = null
  }

  private fun createActionToolbar(targetComponent: JComponent): ActionToolbar? {
    val group = CustomActionsSchema.getInstance().getCorrectedAction(actionGroupId) as? ActionGroup ?: return null
    val toolbar = object: ActionToolbarImpl(ActionPlaces.EDITOR_TOOLBAR, group, true) {
      override fun addNotify() {
        super.addNotify()
        updateActionsImmediately(true)
      }
    }
    toolbar.targetComponent = targetComponent
    toolbar.setReservePlaceAutoPopupIcon(false)
    return toolbar
  }

  private fun showOrUpdateLocation(hint: LightweightHint) {
    HintManagerImpl.getInstanceImpl().showEditorHint(
      hint,
      editor,
      getHintPosition(hint),
      HintManager.HIDE_BY_ESCAPE or HintManager.UPDATE_BY_SCROLLING,
      0,
      true
    )
  }

  private fun registerListeners() {
    editor.addEditorMouseListener(mouseListener)
    editor.addEditorMouseMotionListener(mouseMotionListener)
    editor.contentComponent.addKeyListener(keyboardListener)
  }

  private fun unregisterListeners() {
    editor.removeEditorMouseListener(mouseListener)
    editor.removeEditorMouseMotionListener(mouseMotionListener)
    editor.contentComponent.removeKeyListener(keyboardListener)
  }

  private fun canBeShownAtCurrentSelection(): Boolean {
    val file = PsiEditorUtil.getPsiFile(editor)
    PsiDocumentManager.getInstance(file.project).commitDocument(editor.document)
    val selectionModel = editor.selectionModel
    val elementAtStart = PsiUtilCore.getElementAtOffset(file, selectionModel.selectionStart)
    val elementAtEnd = PsiUtilCore.getElementAtOffset(file, selectionModel.selectionEnd)
    return !(hasIgnoredParent(elementAtStart) || hasIgnoredParent(elementAtEnd))
  }

  protected open fun hasIgnoredParent(element: PsiElement): Boolean {
    if (element.containingFile !is MarkdownFile) {
      return true
    }
    return element.parents(withSelf = true).any { it.elementType in elementsToIgnore }
  }

  private fun getHintPosition(hint: LightweightHint): Point {
    val hintPos = HintManagerImpl.getInstanceImpl().getHintPosition(hint, editor, HintManager.DEFAULT)
    // because of `hint.setForceShowAsPopup(true)`, HintManager.ABOVE does not place the hint above
    // the hint remains on the line, so we need to move it up ourselves
    val dy = -(hint.component.preferredSize.height + verticalGap)
    val dx = buttonSize * -2
    hintPos.translate(dx, dy)
    return hintPos
  }

  private fun updateOnProbablyChangedSelection(onSelectionChanged: (String) -> Unit) {
    val newSelection = editor.selectionModel.selectedText

    when (newSelection) {
      null -> hideIfShown()
      lastSelection -> Unit
      else -> onSelectionChanged(newSelection)
    }

    lastSelection = newSelection
  }

  private inner class MouseListener : EditorMouseListener {
    override fun mouseReleased(e: EditorMouseEvent) {
      updateOnProbablyChangedSelection {
        if (isShown()) {
          updateLocationIfShown()
        } else {
          showIfHidden()
        }
      }
    }
  }

  private inner class KeyboardListener : KeyAdapter() {
    override fun keyReleased(e: KeyEvent) {
      super.keyReleased(e)
      if (e.source != editor.contentComponent) {
        return
      }
      updateOnProbablyChangedSelection {
        hideIfShown()
      }
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
      if (hoverSelected) {
        showIfHidden()
      }
    }
  }

  companion object {
    private const val verticalGap = 2

    private val elementsToIgnore = listOf(
      MarkdownElementTypes.CODE_FENCE,
      MarkdownElementTypes.CODE_BLOCK,
      MarkdownElementTypes.CODE_SPAN,
      MarkdownElementTypes.HTML_BLOCK,
      MarkdownElementTypes.LINK_DESTINATION
    )
  }
}
