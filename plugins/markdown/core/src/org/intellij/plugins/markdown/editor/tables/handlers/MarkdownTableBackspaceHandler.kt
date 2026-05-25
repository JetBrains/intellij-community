// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.handlers

import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

internal class MarkdownTableBackspaceHandler: BackspaceHandlerDelegate() {
  override fun beforeCharDeleted(c: Char, file: PsiFile, editor: Editor) = Unit

  override fun charDeleted(char: Char, file: PsiFile, editor: Editor): Boolean {
    return reformatTableColumnAfterCharDeletion(char, file, editor)
  }
}
