// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.floating

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.fileEditor.impl.text.TextEditorImpl
import org.intellij.plugins.markdown.lang.MarkdownLanguage

private class FloatingToolbarListenersManager : EditorFactoryListener {
  private val toolbarMap = mutableMapOf<Editor, FloatingToolbar>()

  override fun editorCreated(event: EditorFactoryEvent) {
    val editor = event.editor
    if (editor.project == null || TextEditorImpl.getDocumentLanguage(editor) != MarkdownLanguage.INSTANCE) return

    toolbarMap[editor] = FloatingToolbar(editor)
  }

  override fun editorReleased(event: EditorFactoryEvent) {
    toolbarMap[event.editor]?.unregisterListeners()
    toolbarMap -= event.editor // to prevent project leak
  }
}
