// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.markdown.frontend.editor.tables.handlers

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import org.intellij.plugins.markdown.editor.tables.TableFormattingUtils.reformatColumnOnChange
import org.intellij.plugins.markdown.editor.tables.TableUtils
import org.intellij.plugins.markdown.lang.isMarkdownType

internal class MarkdownTableTypedHandler: TypedHandlerDelegate() {
  override fun charTyped(char: Char, project: Project, editor: Editor, file: PsiFile): Result {
    if (!TableUtils.isFormattingOnTypeEnabledForTables(file) || !file.fileType.isMarkdownType()) {
      return super.charTyped(char, project, editor, file)
    }
    val caretOffset = editor.caretModel.currentCaret.offset
    val document = editor.document
    if (!TableUtils.isProbablyInsideTableCell(document, caretOffset)) {
      return super.charTyped(char, project, editor, file)
    }
    PsiDocumentManager.getInstance(project).commitDocument(document)
    val table = TableUtils.findTable(file, caretOffset)
    val cellIndex = TableUtils.findCellIndex(file, caretOffset)
    if (table == null || cellIndex == null) {
      return super.charTyped(char, project, editor, file)
    }
    executeCommand(project) {
      table.reformatColumnOnChange(
        document,
        editor.caretModel.allCarets,
        cellIndex,
        trimToMaxContent = true
      )
    }
    return Result.STOP
  }
}
