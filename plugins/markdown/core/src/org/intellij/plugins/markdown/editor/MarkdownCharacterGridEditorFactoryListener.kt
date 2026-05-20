// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor

import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.impl.EditorImpl

/**
 * Activates character-grid mode on editors that are created outside the [com.intellij.openapi.fileEditor.TextEditor]
 * infrastructure — most importantly the diff editor inside the IntelliJ IntentionPreview popup — so that CJK
 * characters in a Markdown table render at exactly two grid cells and the pipes line up with the separator's
 * dashes. [MarkdownCharacterGridCustomizer] handles the main editor; that path doesn't fire here because the
 * `TextEditorCustomizer` extension point isn't used by [com.intellij.openapi.editor.EditorFactory.createEditor].
 */
internal class MarkdownCharacterGridEditorFactoryListener : EditorFactoryListener {
  override fun editorCreated(event: EditorFactoryEvent) {
    val editor = event.editor as? EditorImpl ?: return
    // The IntentionPreview popup creates its editor via the 4-arg EditorFactory.createEditor
    // overload, which leaves `editorKind` at the default UNTYPED — so accept UNTYPED here, alongside
    // PREVIEW/DIFF. MAIN_EDITOR is already handled by MarkdownCharacterGridCustomizer, and CONSOLE
    // never wants grid mode.
    if (editor.editorKind == EditorKind.MAIN_EDITOR || editor.editorKind == EditorKind.CONSOLE) return
    val text = editor.document.immutableCharSequence
    if (!text.hasFullWidth() || !text.hasTableSeparator()) return
    enableGridMode(editor)
  }
}
