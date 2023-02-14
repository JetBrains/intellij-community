package com.intellij.refactoring.detector.semantic.diff

import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.codeInsight.hints.presentation.PresentationRenderer
import com.intellij.openapi.editor.InlayProperties
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer

class TextInlay(private val text: String) : SemanticInlay {
  fun addTo(editor: EditorImpl, offset: Int): SemanticInlay {
    with(PresentationFactory(editor)) {
      val textInlay =
        inset(top = 7,
              base = seq(
                text(text),
              )
        )

      val inlay = editor.inlayModel.addBlockElement(offset, InlayProperties().showAbove(true), PresentationRenderer(textInlay))!!
      Disposer.register(this@TextInlay, inlay)
    }
    return this
  }


  override fun dispose() {}
}
