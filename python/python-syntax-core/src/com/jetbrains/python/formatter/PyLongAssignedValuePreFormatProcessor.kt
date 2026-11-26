// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.lang.ASTNode
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.psi.impl.source.codeStyle.PreFormatProcessor
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.ast.PyAstAssignmentStatement
import com.jetbrains.python.ast.PyAstAugAssignmentStatement
import com.jetbrains.python.ast.PyAstBinaryExpression
import com.jetbrains.python.ast.PyAstConditionalExpression
import com.jetbrains.python.ast.PyAstExpression
import com.jetbrains.python.ast.PyAstParenthesizedExpression
import com.jetbrains.python.ast.PyAstRecursiveElementVisitor
import com.jetbrains.python.ast.PyAstStatement
import com.jetbrains.python.ast.PyAstTupleExpression
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyAstElementGenerator
import org.jetbrains.annotations.ApiStatus

/**
 * Puts a long assigned value on its own line in parentheses, as Black and Ruff do:
 *
 * ```
 * some_dictionary["some_key"] = (
 *     some_long_expression
 * )
 * ```
 *
 * The processor runs when [PyCodeStyleSettings.WRAP_LONG_ASSIGNED_VALUE_IN_PARENTHESES] is on.
 * It changes a statement only when the statement does not fit on one line within the right margin.
 * The line with the targets and the opening parenthesis must fit. Otherwise, the formatter splits the targets.
 * The value must also fit on its own line, or be an expression that the formatter can split,
 * or the targets must have brackets that the formatter would split instead.
 * A tuple gets one item on each line.
 * A value that ends with a pair of brackets that is not empty keeps its own brackets, so the formatter splits inside
 * them. A binary expression with two or more operators of its lowest priority still gets the parentheses.
 * A value that already is in parentheses gets a line break after the opening and before the closing parenthesis.
 * A statement with a comment, a backslash continuation or a multi-line string stays as it is.
 * The processor estimates the length on the text before the formatter changes the spaces.
 */
@ApiStatus.Internal
class PyLongAssignedValuePreFormatProcessor : PreFormatProcessor {
  override fun process(element: ASTNode, range: TextRange): TextRange {
    val psiElement = element.psi ?: return range
    // Not a doctest or a code block in a docstring: the parentheses would break their prompts and indent.
    if (!psiElement.language.`is`(PythonLanguage.getInstance())) return range
    val file = psiElement.containingFile ?: return range
    if (InjectedLanguageManager.getInstance(file.project).isInjectedFragment(file)) return range
    val settings = CodeStyle.getSettings(file)
    if (!settings.getCustomSettings(PyCodeStyleSettings::class.java).WRAP_LONG_ASSIGNED_VALUE_IN_PARENTHESES) return range
    val rightMargin = settings.getRightMargin(PythonLanguage.getInstance())
    val indentSize = settings.getIndentOptions(file.fileType).INDENT_SIZE

    val values = mutableListOf<PyAstExpression>()
    psiElement.accept(object : PyAstRecursiveElementVisitor() {
      override fun visitPyAssignmentStatement(node: PyAstAssignmentStatement) {
        addValue(node, node.assignedValue)
        super.visitPyAssignmentStatement(node)
      }

      override fun visitPyAugAssignmentStatement(node: PyAstAugAssignmentStatement) {
        addValue(node, node.value)
        super.visitPyAugAssignmentStatement(node)
      }

      private fun addValue(statement: PyAstStatement, value: PyAstExpression?) {
        if (value != null && range.contains(statement.textRange) && isAfterAssignmentOperator(value) &&
            needsParentheses(statement, value, rightMargin, indentSize)) {
          values.add(value)
        }
      }
    })

    var delta = 0
    // From the end, so that a replacement does not move the elements that are still to be replaced.
    for (value in values.asReversed()) {
      val oldLength = value.textLength
      delta += parenthesize(value).textLength - oldLength
    }
    return TextRange(range.startOffset, range.endOffset + delta)
  }
}

private fun parenthesize(value: PyAstExpression): PsiElement {
  val content = (value as? PyAstParenthesizedExpression)?.containedExpression ?: value
  val contentText = when (content) {
    // A tuple with one item keeps its comma. The trailing comma processor adds the comma to a longer tuple.
    is PyAstTupleExpression<*> -> content.elements.joinToString(",\n") { it.text } + if (content.elements.size == 1) "," else ""
    else -> content.text
  }
  val generator = PyAstElementGenerator.getInstance(value.project)
  val parenthesized = generator.createExpressionFromText(LanguageLevel.forElement(value), "(\n$contentText\n)")
  // The running reformat formats the new element. A postponed reformat would lock the file,
  // and then the long-line wrap after the reformat would skip the file.
  return PostprocessReformattingAspect.getInstance(value.project).disablePostprocessFormattingInside(Computable {
    value.replace(parenthesized)
  })
}

private fun isAfterAssignmentOperator(value: PyAstExpression): Boolean {
  val operator = PsiTreeUtil.skipWhitespacesBackward(value)?.elementType
  return operator == PyTokenTypes.EQ || PyTokenTypes.AUG_ASSIGN_OPERATIONS.contains(operator)
}

private fun needsParentheses(statement: PyAstStatement, value: PyAstExpression, rightMargin: Int, indentSize: Int): Boolean {
  val content: PyAstExpression
  if (value is PyAstParenthesizedExpression) {
    content = value.containedExpression ?: return false
    // A tuple and a generator have their own layout in parentheses.
    if (content is PyAstTupleExpression<*> || hasLineBreaksInside(value) || splitsInOwnBrackets(content)) return false
  }
  else {
    content = value
    if (splitsInOwnBrackets(value)) return false
  }
  val indent = indentOf(statement)
  val statementLength = flatLength(statement) ?: return false
  if (indent + statementLength <= rightMargin) return false
  val valueLength = flatLength(value) ?: return false
  // The targets, the operator and the space before the value, then the opening parenthesis.
  if (indent + statementLength - valueLength + 1 > rightMargin) return false
  val contentLength = flatLength(content) ?: return false
  return indent + indentSize + contentLength <= rightMargin || isSplittable(content) || hasBracketsBefore(statement, value)
}

/**
 * Tells if the formatter splits the expression inside the brackets at its end, as Black does.
 */
private fun splitsInOwnBrackets(expression: PyAstExpression): Boolean {
  val closingBracket = PsiTreeUtil.lastChild(expression)
  if (!PyTokenTypes.CLOSE_BRACES.contains(closingBracket.elementType)) return false
  // Empty brackets cannot split.
  if (PyTokenTypes.OPEN_BRACES.contains(PsiTreeUtil.skipWhitespacesBackward(closingBracket)?.elementType)) return false
  return expression !is PyAstBinaryExpression || lowestPriorityOperatorCount(expression) < 2
}

/**
 * Counts the operators of the binary expression that have the priority of its own operator,
 * from the left operands that are binary expressions too. For `a + b - f(c)`, the result is 2.
 */
private fun lowestPriorityOperatorCount(expression: PyAstBinaryExpression): Int {
  val priority = priorityGroup(expression.operator)
  var count = 0
  var current: PyAstExpression? = expression
  while (current is PyAstBinaryExpression && priorityGroup(current.operator) == priority) {
    count++
    current = current.leftExpression
  }
  return count
}

private val PRIORITY_GROUPS = listOf(
  PyTokenTypes.ADDITIVE_OPERATIONS,
  PyTokenTypes.MULTIPLICATIVE_OPERATIONS,
  PyTokenTypes.SHIFT_OPERATIONS,
  PyTokenTypes.BITWISE_OPERATIONS,
  PyTokenTypes.COMPARISON_OPERATIONS,
)

private fun priorityGroup(operator: IElementType?): Any? = PRIORITY_GROUPS.firstOrNull { it.contains(operator) } ?: operator

private fun hasBracketsBefore(statement: PyAstStatement, value: PyAstExpression): Boolean {
  var leaf: PsiElement? = PsiTreeUtil.firstChild(statement)
  while (leaf != null && leaf.textRange.endOffset <= value.textRange.startOffset) {
    if (PyTokenTypes.CLOSE_BRACES.contains(leaf.elementType)) return true
    leaf = PsiTreeUtil.nextLeaf(leaf)
  }
  return false
}

private fun hasLineBreaksInside(parenthesized: PyAstParenthesizedExpression): Boolean =
  (parenthesized.firstChild.nextSibling as? PsiWhiteSpace)?.textContains('\n') == true &&
  (parenthesized.lastChild.prevSibling as? PsiWhiteSpace)?.textContains('\n') == true

private fun isSplittable(expression: PyAstExpression): Boolean =
  expression is PyAstBinaryExpression || expression is PyAstConditionalExpression || expression is PyAstTupleExpression<*>

private fun indentOf(statement: PyAstStatement): Int {
  val text = statement.containingFile.viewProvider.contents
  val start = statement.textRange.startOffset
  return start - (StringUtil.lastIndexOf(text, '\n', 0, start) + 1)
}

/**
 * Returns the length of the element text when the formatter joins it on one line, or `null` when the element
 * cannot be on one line. Whitespace after an opening bracket or before a closing bracket becomes no space.
 * Other whitespace becomes one space. A comma, an assignment operator and a binary operator get the spaces
 * that the formatter adds around them.
 */
private fun flatLength(element: PsiElement): Int? {
  val end = element.textRange.endOffset
  var length = 0
  var leaf: PsiElement? = PsiTreeUtil.firstChild(element)
  while (leaf != null && leaf.textRange.endOffset <= end) {
    val previous = PsiTreeUtil.prevLeaf(leaf)
    val next = PsiTreeUtil.nextLeaf(leaf)
    when {
      leaf is PsiComment -> return null
      leaf is PsiWhiteSpace -> {
        if (leaf.textContains('\\')) return null
        val afterOpening = PyTokenTypes.OPEN_BRACES.contains(previous?.elementType)
        val beforeClosing = PyTokenTypes.CLOSE_BRACES.contains(next?.elementType)
        if (!afterOpening && !beforeClosing) length++
      }
      leaf.textContains('\n') -> return null
      else -> {
        length += leaf.textLength
        if (leaf.elementType == PyTokenTypes.COMMA) {
          if (next !is PsiWhiteSpace && !PyTokenTypes.CLOSE_BRACES.contains(next?.elementType)) length++
        }
        else if (isSpacedOperator(leaf)) {
          if (previous !is PsiWhiteSpace) length++
          if (next !is PsiWhiteSpace) length++
        }
      }
    }
    leaf = next
  }
  return length
}

private fun isSpacedOperator(leaf: PsiElement): Boolean {
  val type = leaf.elementType
  val parent = leaf.parent
  return (type == PyTokenTypes.EQ || PyTokenTypes.AUG_ASSIGN_OPERATIONS.contains(type)) &&
         (parent is PyAstAssignmentStatement || parent is PyAstAugAssignmentStatement) ||
         parent is PyAstBinaryExpression && leaf == parent.psiOperator
}
