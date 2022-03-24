// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.smart

import com.intellij.codeInsight.editorActions.moveUpDown.LineMover
import com.intellij.codeInsight.editorActions.moveUpDown.LineRange
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.yaml.YAMLElementTypes
import org.jetbrains.yaml.YAMLLanguage
import org.jetbrains.yaml.psi.impl.YAMLBlockMappingImpl
import org.jetbrains.yaml.psi.impl.YAMLBlockSequenceImpl

class YAMLStatementMover : LineMover() {
  override fun checkAvailable(editor: Editor, file: PsiFile, info: MoveInfo, down: Boolean): Boolean {
    if (!file.viewProvider.hasLanguage(YAMLLanguage.INSTANCE)) return false
    val offset = editor.caretModel.offset
    val selectionModel = editor.selectionModel
    val document = editor.document
    val lineNumber = document.getLineNumber(offset)
    val start: Int
    val end: Int
    if (selectionModel.hasSelection()) {
      start = selectionModel.selectionStart
      val selectionEnd = selectionModel.selectionEnd
      end = if (selectionEnd == 0) 0 else selectionEnd - 1
    }
    else {
      start = getLineStartSafeOffset(document, lineNumber)
      val lineEndOffset = document.getLineEndOffset(lineNumber)
      end = if (lineEndOffset == 0) 0 else lineEndOffset - 1
    }
    var elementToMove1 = findNextAtOffset(file, start, document) ?: return false
    var elementToMove2 = findPrevAtOffset(file, end, document) ?: return false
    if (PsiTreeUtil.isAncestor(elementToMove1, elementToMove2, false)) {
      elementToMove2 = elementToMove1
    }
    else if (PsiTreeUtil.isAncestor(elementToMove2, elementToMove1, false)) {
      elementToMove1 = elementToMove2
    }
    if (elementToMove2 !== elementToMove1) {
      val commonParent = PsiTreeUtil.findCommonParent(listOf(elementToMove1, elementToMove2))
                         ?: return false
      val moveScope = PsiTreeUtil.getNonStrictParentOfType(commonParent, YAMLBlockMappingImpl::class.java,
                                                           YAMLBlockSequenceImpl::class.java)
                      ?: return false
      if (elementToMove1 !== moveScope) {
        while (elementToMove1.parent !== moveScope) {
          elementToMove1 = elementToMove1.parent ?: return false
        }
      }
      if (elementToMove2 !== moveScope) {
        while (elementToMove2.parent !== moveScope) {
          elementToMove2 = elementToMove2.parent ?: return false
        }
      }
    }
    val destination = getDestinationScope(file, (if (down) elementToMove2 else elementToMove1), down) ?: return false
    info.toMove = LineRange(elementToMove1, elementToMove2)
    info.toMove2 = destination
    info.indentTarget = false
    info.indentSource = false
    return true
  }

  private fun findNextAtOffset(psiFile: PsiFile, beginAt: Int, document: Document): PsiElement? {
    var offset = beginAt
    while (offset < document.textLength) {
      if (!document.charsSequence[offset].isWhitespace()) {
        return psiFile.findElementAt(offset)
      }
      offset++
    }
    return null
  }

  private fun findPrevAtOffset(psiFile: PsiFile, beginAt: Int, document: Document): PsiElement? {
    var offset = beginAt
    while (offset >= 0) {
      if (!document.charsSequence[offset].isWhitespace()) {
        return psiFile.findElementAt(offset)
      }
      offset--
    }
    return null
  }

  private fun getDestinationScope(file: PsiFile, elementToMove: PsiElement, down: Boolean): LineRange? {
    val document = file.viewProvider.document ?: return null
    val offset = if (down) elementToMove.textRange.endOffset else elementToMove.textRange.startOffset
    val lineNumber = if (down) document.getLineNumber(offset) + 1 else document.getLineNumber(offset) - 1
    if (lineNumber < 0 || lineNumber >= document.lineCount) return null
    val destination = getDestinationElement(elementToMove, down) ?: return null
    val startLine = document.getLineNumber(destination.textRange.startOffset)
    val endLine = document.getLineNumber(destination.textRange.endOffset)
    return LineRange(startLine, endLine + 1)
  }

  private fun getDestinationElement(elementToMove: PsiElement, down: Boolean): PsiElement? {
    var destination: PsiElement? = elementToMove
    do {
      destination = if (down) destination?.nextSibling else destination?.prevSibling
    }
    while (destination != null && YAMLElementTypes.SPACE_ELEMENTS.contains(PsiUtilCore.getElementType(destination)))
    return if (destination == elementToMove) null else destination
  }
}
