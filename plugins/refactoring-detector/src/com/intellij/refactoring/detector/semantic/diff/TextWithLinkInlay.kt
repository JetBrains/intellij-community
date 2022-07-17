// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.detector.semantic.diff
import com.intellij.codeInsight.hints.InlayPresentationFactory
import com.intellij.codeInsight.hints.presentation.BiStatePresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.codeInsight.hints.presentation.PresentationRenderer
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.InlayProperties
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import java.awt.Cursor
import java.awt.Point
import java.awt.event.MouseEvent

interface SemanticInlay : Disposable

class TextWithLinkInlay(private val text: String,
                        private val linkText: String,
                        private val clickedLinkText: String = linkText,
                        private val onClick: () -> Unit = {}) : SemanticInlay {

  private lateinit var state: BiStatePresentation

  fun addTo(editor: EditorImpl, offset: Int): SemanticInlay {
    with(PresentationFactory(editor)) {
      val linkDefault = withReferenceAttributes(text(linkText))
      val linkActive = withReferenceAttributes(text(clickedLinkText))

      val (button, state) = button(linkDefault, linkActive, ClickListener(onClick), HoverListener(editor), true)
      this@TextWithLinkInlay.state = state
      val textWithLinkInlay =
        inset(top = 7,
              base = seq(
                text(text),
                textSpacePlaceholder(1, true),
                button
              )
        )

      val inlay = editor.inlayModel.addBlockElement(offset, InlayProperties().showAbove(true), PresentationRenderer(textWithLinkInlay))!!
      Disposer.register(this@TextWithLinkInlay, inlay)
    }

    return this
  }

  fun flipState() {
    if (::state.isInitialized) {
      state.flipState()
    }
  }

  private class ClickListener(private val onClick: () -> Unit) : InlayPresentationFactory.ClickListener {
    override fun onClick(event: MouseEvent, translated: Point) {
      onClick()
    }
  }

  private class HoverListener(val editor: EditorImpl) : InlayPresentationFactory.HoverListener {
    override fun onHover(event: MouseEvent, translated: Point) {
      val defaultCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      editor.setCustomCursor(this, defaultCursor)
    }

    override fun onHoverFinished() {
      editor.setCustomCursor(this, null)
    }
  }

  override fun dispose() {}
}
