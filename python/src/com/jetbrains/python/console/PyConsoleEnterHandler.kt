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
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyStringLiteralExpressionImpl

/**
 * @author Yuli Fiterman
 */

/**
 *  Implementation notes:
 *  This class determines whether the console input should be sent to the console.
 *  The goal is to be consistent with IPython's logic for handling this in as many cases as possible ,
 *  with the exception of input errors.
 *
 *  It is not necessary to handle input errors in the same way as IPython because PyCharm has error highlighting so
 *  we can present the error to the user without having to execute the input. Also, attempting to make input error
 *  handling 100% consistent would be a near impossible task, as IPython delegates this to the Python interpreter
 *
 *  When in doubt it is better to err on the side of NOT sending the input. The console implementation does handle
 *  the case where we send incomplete input, but not very well. Therefore any case where we end up sending incomplete input
 *  should be considered an implementation error.
 *
 *  For reference, the IPython implementation of input handling can be found at:
 *  https://github.com/ipython/ipython/blob/master/IPython/core/inputsplitter.py
 */
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
    val prevLine = editor.document.getLineAtOffset(caretOffset)

    val psiFile = psiMgr.getPsiFile(editor.document)!!
    val isComplete = BlockCompletionChecker().checkComplete(psiFile)
    val isStandaloneStatement = canStatementBeExecuted(psiFile, caretOffset)
    val enterHandler = EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER)
    object : WriteCommandAction<Nothing>(project) {
      @Throws(Throwable::class)
      override fun run(result: Result<Nothing>) {
        enterHandler.execute(editor, null, DataManager.getInstance().getDataContext(editor.component))
      }
    }.execute()

    val isCellMagic = prevLine.trim().startsWith("%%") && !prevLine.trimEnd().endsWith("?")
    val isCellHelp = prevLine.trim().startsWith("%%") && prevLine.trimEnd().endsWith("?")
    val isLineCellMagic = prevLine.trim().startsWith("%")
    val isLineContinuation = prevLine.trim().endsWith('\\')

    val allBlocksComplete = !isCellMagic && !isLineContinuation &&
        (isCellHelp || isLineCellMagic || isComplete)

    val canExecuteStandalone = !isLineContinuation && (isCellHelp || isLineCellMagic || isStandaloneStatement)


    return (canExecuteStandalone || prevLine.isBlank()) && (allBlocksComplete)
  }


  private fun Document.getLineAtOffset(offset: Int): String {
    val line = getLineNumber(offset)
    val start = getLineStartOffset(line)
    val end = getLineEndOffset(line)
    return getText(TextRange(start, end))
  }

  private fun canStatementBeExecuted(psiFile: PsiFile, offset: Int): Boolean {
    val elem = findFirstNonSpaceElement(psiFile, offset) ?: return false
    val pyStmt = PsiTreeUtil.getNonStrictParentOfType(elem, PyStatement::class.java) ?: return false
    return PsiTreeUtil.getParentOfType(pyStmt, PyStatementList::class.java) == null
        && PsiTreeUtil.findChildOfAnyType(pyStmt, PsiErrorElement::class.java) == null

  }

  private fun findFirstNonSpaceElement(psiFile: PsiFile, offset: Int): PsiElement? {
    for (i in offset downTo 0) {
      val el = psiFile.findElementAt(i)
      if (el != null && el !is PsiWhiteSpace) {
        return el
      }
    }
    return null
  }


}

private class BlockCompletionChecker : PyRecursiveElementVisitor() {
  var result = true
  private fun hasNonErrorElements(stmtList: PyStatementList?): Boolean {
    val statements = stmtList?.statements ?: return false
    return statements.any { it !is PsiErrorElement }
  }

  private fun isCompleteDocString(str: String): Boolean {
    val prefixLen = PyStringLiteralExpressionImpl.getPrefixLength(str)
    val text = str.substring(prefixLen)
    for (token in arrayOf("\"\"\"", "'''")) {
      if (text.length >= 2 * token.length && text.startsWith(token) && text.endsWith(token)) {
        return true
      }
    }

    return false

  }

  override fun visitPyTryExceptStatement(node: PyTryExceptStatement) {
    val tryNotEmpty = hasNonErrorElements(node.tryPart.statementList)
    val exceptNotEmpty = node.exceptParts.any { hasNonErrorElements(it.statementList) }
    val finallyNotEmpty = hasNonErrorElements(node.finallyPart?.statementList)
    val isComplete = tryNotEmpty && (exceptNotEmpty || finallyNotEmpty)
    if (!isComplete) {
      result = false
      return
    }
    super.visitPyTryExceptStatement(node)
  }

  override fun visitPyStringLiteralExpression(elem: PyStringLiteralExpression) {
    if ((PyTokenTypes.TRIPLE_NODES.contains(elem.node.firstChildNode.elementType) || elem.node.firstChildNode.elementType === PyTokenTypes.DOCSTRING)) {
      if (!isCompleteDocString(elem.text)) {
        result = false
        return
      }
    }
    super.visitPyStringLiteralExpression(elem)
  }

  override fun visitPyStatementList(node: PyStatementList) {
    if (!hasNonErrorElements(node)) {
      result = false
      return
    }

    super.visitPyStatementList(node)
  }

  fun checkComplete(node: PsiElement): Boolean {
    node.accept(this)
    return result
  }
}
