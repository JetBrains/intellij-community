/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.console

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.Result
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.codeStyle.IndentHelperImpl
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.psi.PyStatementListContainer
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.impl.PyStringLiteralExpressionImpl


class PyConsoleEnterHandler {
  fun handleEnterPressed(editor: EditorEx): Boolean {
    val project = editor.project ?: throw IllegalArgumentException()
    if (editor.document.lineCount != 0) { // move to end of line
      editor.selectionModel.removeSelection()
      val caretPosition = editor.caretModel.logicalPosition
      val lineEndOffset = editor.document.getLineEndOffset(caretPosition.line)
      editor.caretModel.moveToOffset(lineEndOffset)
    }
    val psiMgr = PsiDocumentManager.getInstance(project)
    psiMgr.commitDocument(editor.document)

    val caretOffset = editor.expectedCaretOffset
    val atElement = findFirstNoneSpaceElement(psiMgr.getPsiFile(editor.document)!!, caretOffset)
    var insideDocString = false
    atElement?.let {
      insideDocString = isElementInsideDocString(atElement, caretOffset)
    }

    val enterHandler = EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER)
    object : WriteCommandAction<Nothing>(project) {
      @Throws(Throwable::class)
      override fun run(result: Result<Nothing>) {
        enterHandler.execute(editor, null, DataManager.getInstance().getDataContext(editor.component))
      }
    }.execute()

    val prevLine = getLineAtOffset(editor.document, caretOffset)
    val isCellMagic = prevLine.trim().startsWith("%%") && !prevLine.trimEnd().endsWith("?")
    val isCellHelp = prevLine.trim().startsWith("%%") && prevLine.trimEnd().endsWith("?")
    val isLineCellMagic = prevLine.trim().startsWith("%")
    val hasCompleteStatement = atElement != null && !insideDocString && !isCellMagic &&
        (isCellHelp || isLineCellMagic || checkComplete(atElement))

    val currentLine = getLineAtOffset(editor.document, editor.expectedCaretOffset)
    val indent = IndentHelperImpl.getIndent(project, PythonFileType.INSTANCE, currentLine, false)

    return indent == 0 || (hasCompleteStatement && prevLine.isBlank())
  }

  private fun isElementInsideDocString(atElement: PsiElement, caretOffset: Int): Boolean {
    return (atElement.context is PyStringLiteralExpression &&
        (PyTokenTypes.TRIPLE_NODES.contains(atElement.node.elementType)
            || atElement.node.elementType === PyTokenTypes.DOCSTRING)
        && (atElement.textRange.endOffset > caretOffset || !isCompletDocString(atElement.text)))
  }

  private fun checkComplete(el: PsiElement): Boolean {
    val compoundStatement = PsiTreeUtil.getParentOfType(el, PyStatementListContainer::class.java)
    if (compoundStatement != null) {
      return compoundStatement.statementList.statements.size != 0
    }
    val topLevel = PyPsiUtils.getParentRightBefore(el, el.containingFile)
    return topLevel != null && PsiTreeUtil.hasErrorElements(topLevel)
  }

  private fun findFirstNoneSpaceElement(psiFile: PsiFile, offset: Int): PsiElement? {
    for (i in offset downTo 0) {
      val el = psiFile.findElementAt(i)
      if (el != null && el !is PsiWhiteSpace) {
        return el
      }
    }
    return null
  }

  private fun getLineAtOffset(doc: Document, offset: Int): String {
    val line = doc.getLineNumber(offset)
    val start = doc.getLineStartOffset(line)
    val end = doc.getLineEndOffset(line)
    return doc.getText(TextRange(start, end))
  }

  private fun isCompletDocString(str: String): Boolean {
    val prefixLen = PyStringLiteralExpressionImpl.getPrefixLength(str)
    val text = str.substring(prefixLen)
    for (token in arrayOf("\"\"\"", "'''")) {
      if (text.length >= 2 * token.length && text.startsWith(token) && text.endsWith(token)) {
        return true
      }
    }

    return false

  }
}