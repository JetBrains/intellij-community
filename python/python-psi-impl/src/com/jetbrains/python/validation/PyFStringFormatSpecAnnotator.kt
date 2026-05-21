// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.validation

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.highlighting.PyHighlighter
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFStringFragment
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

    val memberNames = type.asUnionSequence().map { (it as? PyClassType)?.classQName }
    return when {
      memberNames.any { it in NUMERIC_OR_STR_TYPE_NAMES } -> ExpressionType.STRING_OR_NUMERIC
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

  private fun highlightFormatSpecComponents(element: PsiElement, text: String, expressionType: ExpressionType) {
    val baseOffset = element.textRange.startOffset

    when (expressionType) {
      ExpressionType.STRING_OR_NUMERIC -> {
        // Parse the format spec structure to properly identify fill character
        // Format: [[fill]align][sign][z][#][0][width][grouping][.precision][grouping][type]

        // Check for fill+align (fill is any char, align is <, >, ^, =)
        // If second char is alignment, then first char is fill (don't highlight)
        val fillIdx = if (text.length > 1 && text[1] in STRING_ALIGNMENT_CHARS) 0 else -1

        // Track whether we've seen any digits yet (to distinguish 0 flag from width)
        var seenNonZeroDigit = false
        var seenZeroFlag = false

        // Now highlight character by character, skipping the fill character
        for (i in text.indices) {
          if (i == fillIdx) continue // Don't highlight fill character

          val char = text[i]
          val range = TextRange(baseOffset + i, baseOffset + i + 1)

          when {
            char in STRING_ALIGNMENT_CHARS -> {
              holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(range)
                .textAttributes(PyHighlighter.PY_FSTRING_FORMAT_SPEC_SPECIAL_CHAR)
                .create()
            }
            char in SIGN_CHARS -> {
              // Sign is valid if:
              // 1. We have fill at position 0 and we're at position 2+ (after fill+align)
              // 2. We have no fill and we're at position 1+ (after alignment, if any)
              // 3. We have no fill and we're at position 0 and it's NOT an alignment char
              val isValidSign = when {
                fillIdx == 0 -> i >= 2  // fill+align, sign starts at 2
                i == 0 -> true  // No fill, position 0, this IS the sign
                else -> true  // No fill, position 1+, after optional alignment
              }
              if (isValidSign) {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                  .range(range)
                  .textAttributes(PyHighlighter.PY_FSTRING_FORMAT_SPEC_SPECIAL_CHAR)
                  .create()
              }
            }
            char in NUMERIC_FLAGS_WITHOUT_ZERO -> {
              // Flags: z, # (but not 0, which can be confused with width)
              holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(range)
                .textAttributes(PyHighlighter.PY_FSTRING_FORMAT_SPEC_SPECIAL_CHAR)
                .create()
            }
            char == '0' -> {
              val nextChar = text.getOrNull(i + 1)
              if (!seenZeroFlag && !seenNonZeroDigit && nextChar != null && nextChar.isDigit()) {
                // This is the 0-padding flag
                seenZeroFlag = true
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                  .range(range)
                  .textAttributes(PyHighlighter.PY_FSTRING_FORMAT_SPEC_SPECIAL_CHAR)
                  .create()
              }
              else {
                // This is part of the width
                seenNonZeroDigit = true
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                  .range(range)
                  .textAttributes(PyHighlighter.PY_FSTRING_FORMAT_SPEC_NUMBER)
                  .create()
              }
            }
            char == '.' -> {
              holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(range)
                .textAttributes(PyHighlighter.PY_FSTRING_FORMAT_SPEC_SPECIAL_CHAR)
                .create()
            }
            char in GROUPING_CHARS -> {
              holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(range)
                .textAttributes(PyHighlighter.PY_FSTRING_FORMAT_SPEC_SPECIAL_CHAR)
                .create()
            }
            char.isDigit() -> {
              // Track that we've seen a non-zero digit (for 0 flag detection)
              seenNonZeroDigit = true
              holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(range)
                .textAttributes(PyHighlighter.PY_FSTRING_FORMAT_SPEC_NUMBER)
                .create()
            }
            char in NUMERIC_FORMAT_TYPES -> {
              // Only highlight format type if it appears at a reasonable position
              val nextChar = text.getOrNull(i + 1)
              if (i == text.lastIndex || nextChar == null || nextChar in " \t}") {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                  .range(range)
                  .textAttributes(PyHighlighter.PY_FSTRING_FORMAT_SPEC_SPECIAL_CHAR)
                  .create()
              }
            }
          }
        }
      }

      ExpressionType.DATETIME -> {
        // Highlight: strftime format codes (%Y, %m, %d, %H, %M, %S, etc.)
        for (i in text.indices) {
          val char = text[i]
          if (char == '%' && i < text.lastIndex) {
            // Highlight the % and the following directive character
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
              .range(TextRange(baseOffset + i, baseOffset + i + 2))
              .textAttributes(PyHighlighter.PY_FSTRING_FORMAT_SPEC_SPECIAL_CHAR)
              .create()
          }
        }
      }

      ExpressionType.UNKNOWN -> {
        // Don't highlight anything
      }
    }
  }

  companion object {
    private val NUMERIC_OR_STR_TYPE_NAMES = setOf(
      "str", "int", "float", "complex",
      "decimal.Decimal", "fractions.Fraction",
      "numpy.int8", "numpy.int16", "numpy.int32", "numpy.int64",
      "numpy.float16", "numpy.float32", "numpy.float64",
      "numpy.complex64", "numpy.complex128"
    )

    private val DATETIME_TYPE_NAMES = setOf(
      "datetime.datetime", "datetime.date", "datetime.time"
    )

    private val NUMERIC_FORMAT_TYPES = setOf(
      'b', 'c', 'd', 'e', 'E', 'f', 'F', 'g', 'G', 'n', 'o', 's', 'x', 'X', '%'
    )

    private val STRING_ALIGNMENT_CHARS = setOf('<', '>', '^', '=')

    private val SIGN_CHARS = setOf('+', '-', ' ')

    private val NUMERIC_FLAGS_WITHOUT_ZERO = setOf('z', '#')

    private val GROUPING_CHARS = setOf(',', '_')
  }
}
