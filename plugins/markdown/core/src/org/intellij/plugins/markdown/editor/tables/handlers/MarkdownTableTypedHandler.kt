// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.handlers

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import org.intellij.plugins.markdown.editor.tables.TableFormattingUtils
import org.intellij.plugins.markdown.editor.tables.TableModificationUtils.isCorrectlyFormatted
import org.intellij.plugins.markdown.editor.tables.TableUtils

internal class MarkdownTableTypedHandler: TypedHandlerDelegate() {
  override fun charTyped(char: Char, project: Project, editor: Editor, file: PsiFile): Result {
    if (!TableUtils.isFormattingOnTypeEnabledForTables(file)) {
      return super.charTyped(char, project, editor, file)
    }
    val caretOffset = editor.caretModel.currentCaret.offset
    val document = editor.document
    if (!TableUtils.isProbablyInsideTableCell(document, caretOffset)) {
      return super.charTyped(char, project, editor, file)
    }
    PsiDocumentManager.getInstance(project).commitDocument(document)
    val table = TableUtils.findTable(file, caretOffset)
    if (table == null) {
      return super.charTyped(char, project, editor, file)
    }
    executeCommand(project) {
      if (!table.isCorrectlyFormatted(checkAlignment = false)) {
        TableFormattingUtils.reformatAllColumns(
          table,
          editor.document,
          trimToMaxContent = true,
          carets = editor.caretModel.allCarets
        )
      }
    }
    return Result.STOP
  }
}
