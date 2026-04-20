// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.editor.selectWord

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase
import com.intellij.codeInsight.editorActions.SelectWordUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.ast.PyAstExpression
import com.jetbrains.python.ast.PyAstFStringFragment
import com.jetbrains.python.ast.PyAstFormattedStringElement
import com.jetbrains.python.ast.PyAstStringLiteralExpression
import com.jetbrains.python.lexer.PyFStringLiteralLexer


class PyFStringSelectionHandler : ExtendWordSelectionHandlerBase() {

  override fun canSelect(e: PsiElement): Boolean {
    val node = e.node ?: return false
    val elementType = node.elementType
    if (elementType in PyTokenTypes.FSTRING_LITERAL_TOKENS) {
      return true
    }
    return PsiTreeUtil.getParentOfType(e, PyAstFStringFragment::class.java, false) != null
  }

  override fun select(
    e: PsiElement,
    editorText: CharSequence,
    cursorOffset: Int,
    editor: Editor
  ): List<TextRange>? {
    val node = e.node ?: return null
    val elementType = node.elementType

    if (elementType in PyTokenTypes.FSTRING_LITERAL_TOKENS) {
      return selectFStringContent(e, editorText, cursorOffset)
    }

    return selectFStringFragment(e)
  }

  private fun selectFStringContent(
    e: PsiElement,
    editorText: CharSequence,
    cursorOffset: Int,
  ): List<TextRange>? {
    val fStringElement = e.parentOfType<PyAstFormattedStringElement>()
                         ?: return null

    val result = mutableListOf<TextRange>()

    val node = e.node
    val elementType = node.elementType
    if (elementType in PyTokenTypes.FSTRING_TEXT_TOKENS) {
      SelectWordUtil.addWordHonoringEscapeSequences(
        editorText, node.textRange, cursorOffset,
        PyFStringLiteralLexer(elementType),
        result
      )
    }

    val contentRange = fStringElement.contentRange
    val fStringStartOffset = fStringElement.textRange.startOffset
    val absoluteContentRange = contentRange.shiftRight(fStringStartOffset)
    if (!absoluteContentRange.isEmpty) {
      result.add(absoluteContentRange)
    }

    val prefixLength = fStringElement.prefixLength
    if (prefixLength > 0) {
      val withoutPrefixRange = TextRange(
        fStringElement.textRange.startOffset + prefixLength,
        fStringElement.textRange.endOffset
      )
      result.add(withoutPrefixRange)
    }

    result.add(fStringElement.parentOfType<PyAstStringLiteralExpression>()?.textRange ?: fStringElement.textRange)

    return result
  }

  private fun selectFStringFragment(e: PsiElement): List<TextRange>? {
    val fragment: PyAstFStringFragment =
      PsiTreeUtil.getParentOfType(e, PyAstFStringFragment::class.java, false)
      ?: return null

    val expression: PyAstExpression = fragment.expression ?: return null
    val exprRange: TextRange = expression.textRange

    val result = mutableListOf<TextRange>()

    result.add(exprRange)

    val typeConversionPsi = fragment.typeConversion
    if (typeConversionPsi != null) {
      val typeConversionRange = typeConversionPsi.textRange
      result.add(TextRange(exprRange.startOffset, typeConversionRange.endOffset))
    }

    val formatPartPsi = fragment.formatPart
    if (formatPartPsi != null) {
      val formatTextRange = formatPartPsi.textRange
      result.add(TextRange(exprRange.startOffset, formatTextRange.endOffset))
    }

    result.add(fragment.textRange)

    val parent = fragment.parent
    if (parent is PyAstFormattedStringElement) {
      result.add(parent.textRange)
    }
    return result
  }
}
