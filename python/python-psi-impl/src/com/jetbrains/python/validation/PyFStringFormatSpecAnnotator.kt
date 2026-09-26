// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.validation

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.codeInsight.fstrings.PyFormatSpec
import com.jetbrains.python.codeInsight.fstrings.PyFormatSpecComponentKind
import com.jetbrains.python.highlighting.PyHighlighter
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFStringFragment
import com.jetbrains.python.psi.PyStringDunderUtil.KNOWN_FORMAT_MINI_LANGUAGE_TYPES
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyTypeUtil.asUnionSequence
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Annotator for f-string format specifications (PY-88215).
 *
 * Highlights format spec components based on the type of the formatted expression:
 * - Numeric types (int, float): dots, numbers, format type chars (b,d,e,f,g,o,x,%)
 * - String types: alignment chars, numbers, format type char (s)
 *
 * Examples:
 * - f"{f:.2f}" where f: float - highlights . 2 f
 * - f"{s:>10s}" where s: str - highlights > 1 0 s
 * - f"{x:.2f}" where x has unknown type - does NOT highlight
 */
class PyFStringFormatSpecAnnotator : PyAnnotatorBase() {
  override fun annotate(element: PsiElement, holder: PyAnnotationHolder) {
    if (holder.isBatchMode()) return
    element.accept(PyFStringFormatSpecVisitor(holder))
  }
}

private class PyFStringFormatSpecVisitor(private val holder: PyAnnotationHolder) : PyElementVisitor() {

  override fun visitPyFStringFragment(fragment: PyFStringFragment) {
    val formatPart = fragment.formatPart ?: return
    val expression = fragment.expression ?: return

    // Check for type conversion (!s, !r, !a)
    val typeConversion = fragment.typeConversion
    val expressionType = if (typeConversion != null) {
      // Type conversion modifiers (!s, !r, !a) all convert to string
      ExpressionType.STRING_OR_NUMERIC
    }
    // No conversion - use the actual expression type
    else expression.getExpressionType()

    if (expressionType == ExpressionType.UNKNOWN) return

    // Highlight format spec components based on type
    highlightFormatSpec(formatPart, expressionType)
  }

  private enum class ExpressionType {
    STRING_OR_NUMERIC, // str, int, float, complex numpy
    DATETIME, // datetime, date, time
    UNKNOWN, // type cannot be determined or is not a standard type
  }

  private fun PyExpression.getExpressionType(): ExpressionType {
    val context = TypeEvalContext.codeAnalysis(project, containingFile)
    val type = context.getType(this) ?: return ExpressionType.UNKNOWN

    val memberNames =
      type.asUnionSequence().flatMapTo(mutableSetOf()) { component ->
        if (component !is PyClassType || component.isDefinition) emptySet()
        else component.getSuperClassTypes(context).map { it?.classQName } + component.classQName
      }

    return when {
      memberNames.any { it in KNOWN_FORMAT_MINI_LANGUAGE_TYPES } -> ExpressionType.STRING_OR_NUMERIC
      memberNames.any { it in DATETIME_TYPE_NAMES } -> ExpressionType.DATETIME
      else -> ExpressionType.UNKNOWN
    }
  }

  private fun highlightFormatSpec(
    formatPart: PsiElement,
    expressionType: ExpressionType,
  ) {
    // Find the start of the format spec content (after the colon)
    val children = formatPart.node.getChildren(null)
    var formatSpecStart: PsiElement? = null
    for (child in children) {
      if (child.elementType == PyTokenTypes.FSTRING_FRAGMENT_FORMAT_START) {
        formatSpecStart = child.psi.nextSibling
        break
      }
    }

    if (formatSpecStart == null) return

    // Traverse the format spec and highlight appropriate components
    var current: PsiElement? = formatSpecStart
    while (current != null && current.parent == formatPart) {
      when (current.node.elementType) {
        PyTokenTypes.FSTRING_TEXT, PyTokenTypes.FSTRING_RAW_TEXT -> {
          highlightFormatSpecComponents(current, current.text, expressionType)
        }
      }
      current = current.nextSibling
    }
  }

  /**
   * Highlights the components that [PyFormatSpec] recognizes in [text]. The parser is shared with the
   * completion contributor and the documentation providers, so all three read a character the same way.
   * The policy stays here: highlighting follows the syntax, and an invalid combination is reported by an
   * inspection instead.
   */
  private fun highlightFormatSpecComponents(element: PsiElement, text: String, expressionType: ExpressionType) {
    val baseOffset = element.textRange.startOffset
    val spec = PyFormatSpec.parse(text, datetime = expressionType == ExpressionType.DATETIME)

    for (component in spec.components) {
      when (component.kind) {
        // A fill character carries no meaning of its own, so it stays unhighlighted.
        PyFormatSpecComponentKind.FILL -> continue
        // A width or a precision is a run of digits, highlighted one digit at a time.
        PyFormatSpecComponentKind.WIDTH, PyFormatSpecComponentKind.PRECISION -> {
          for (offset in component.startOffset until component.endOffset) {
            highlight(TextRange(baseOffset + offset, baseOffset + offset + 1), PyHighlighter.PY_FSTRING_FORMAT_SPEC_NUMBER)
          }
        }
        else -> {
          highlight(TextRange(baseOffset + component.startOffset, baseOffset + component.endOffset),
                    PyHighlighter.PY_FSTRING_FORMAT_SPEC_SPECIAL_CHAR)
        }
      }
    }
  }

  private fun highlight(range: TextRange, attributes: TextAttributesKey) {
    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
      .range(range)
      .textAttributes(attributes)
      .create()
  }

  companion object {
    private val DATETIME_TYPE_NAMES = setOf(
      PyNames.FQN.DATETIME, PyNames.FQN.DATE, PyNames.FQN.TIME
    )
  }
}
