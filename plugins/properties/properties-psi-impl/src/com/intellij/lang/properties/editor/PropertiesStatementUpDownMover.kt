// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.editor

import com.intellij.codeInsight.editorActions.moveUpDown.LineMover
import com.intellij.codeInsight.editorActions.moveUpDown.LineRange
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.lang.properties.psi.Property
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.parentOfType

internal class PropertiesStatementUpDownMover : LineMover() {

  override fun checkAvailable(editor: Editor, file: PsiFile, info: MoveInfo, down: Boolean): Boolean {
    if (file !is PropertiesFile) return false
    if (editor.selectionModel.hasSelection()) {
      return super.checkAvailable(editor, file, info, down)
    }

    val range = getLineRangeFromSelection(editor)
    if (!canMove(editor, range, down)) return false
    val startElement = getElementRange(editor, file, range)?.first ?: return info.prohibitMove()
    val property = startElement.parentOfType<Property>(withSelf = true) ?: return info.prohibitMove()

    info.toMove = LineRange(property)
    info.indentTarget = false
    info.indentSource = false

    val neighbor = findNeighborProperty(property, down) ?: return info.prohibitMove()
    info.toMove2 = LineRange(neighbor)
    return true
  }

  private fun canMove(editor: Editor, range: LineRange, down: Boolean): Boolean {
    if (range.startLine == 0 && !down) return false
    val maxLine = editor.offsetToLogicalPosition(editor.document.textLength).line
    return !down || range.endLine <= maxLine
  }
}

internal fun findNeighborProperty(property: Property, forward: Boolean): Property? {
  var sibling = if (forward) property.nextSibling else property.prevSibling
  while (sibling is PsiWhiteSpace) {
    sibling = if (forward) sibling.nextSibling else sibling.prevSibling
  }
  return sibling as? Property
}