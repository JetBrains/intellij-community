// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.markdown.editor

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile

internal class MarkdownCodeFenceTypedHandlerDelegate : TypedHandlerDelegate() {
  override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
    if (file !is MarkdownFile) return Result.CONTINUE

    val caretModel = editor.caretModel
    val offset = caretModel.offset

    val document = editor.document

    if (c == ':' && offset - 3 >= 0 && document.getText(TextRange(offset - 3, offset)) == ":::") {
      invokeLater {
        CodeCompletionHandlerBase(CompletionType.BASIC, false, false, true)
          .invokeCompletion(project, editor)
      }
    }

    return Result.CONTINUE
  }
}
