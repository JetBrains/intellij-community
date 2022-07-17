// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.ui.presentation

import com.intellij.codeInsight.hints.presentation.DynamicDelegatePresentation
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import java.awt.Cursor
import java.awt.Point
import java.awt.event.MouseEvent

internal class PresentationWithCustomCursor(private val editor: Editor, delegate: InlayPresentation): DynamicDelegatePresentation(delegate) {
  override fun mouseMoved(event: MouseEvent, translated: Point) {
    super.mouseMoved(event, translated)
    val handCursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)
    (editor as? EditorImpl)?.setCustomCursor(cursorRequestor, handCursor)
  }

  override fun mouseExited() {
    (editor as? EditorImpl)?.setCustomCursor(cursorRequestor, null)
    super.mouseExited()
  }

  companion object {
    private val cursorRequestor = Object()
  }
}
