// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inlayHints

import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.parents
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.PyAnnotation
import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PySubscriptionExpression
import com.jetbrains.python.psi.PyTupleExpression
import com.jetbrains.python.psi.PyTypeAliasStatement
import com.jetbrains.python.psi.PyTypeParameter
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Provides inlay hints for Python numeric type promotions.
 *
 * In Python's type system, `float` accepts `int` values, and `complex` accepts both `float` and `int` values.
 * This provider shows hints to indicate these implicit numeric promotions.
 *
 * For example:
 * - `a: float` shows ` | int` hint
 * - `a: complex` shows ` | float | int` hint
 * - `a: float | int` shows no hint (already explicit)
 * - `list[float]` shows ` | int` hint after `float`
 */
class PyNumericPromotionInlayHintsProvider : InlayHintsProvider {
  override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector = Collector()

  private class Collector : SharedBypassCollector {
    private val hintFormat = HintFormat.default

    override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
      if (element !is PyReferenceExpression) return

      if (!element.isInTypeContext()) return

      val builtinCache = PyBuiltinCache.getInstance(element)
      val floatClass = builtinCache.floatType?.pyClass ?: return
      val complexClass = builtinCache.complexType?.pyClass ?: return
      val intClass = builtinCache.intType?.pyClass ?: return

      val resolved = element.reference.resolve()

      val promotionHint = when (resolved) {
                            complexClass -> element.getComplexPromotionHint(floatClass, intClass)
                            floatClass -> element.getFloatPromotionHint(intClass)
                            else -> null
                          } ?: return

      sink.addPresentation(
        position = InlineInlayPosition(element.endOffset, true),
        hintFormat = hintFormat,
        tooltip = "Implicit numeric promotion",
      ) {
        text(promotionHint)
      }
    }

    private fun PyReferenceExpression.isInTypeContext(): Boolean {
      if (parents(false).any { it is PyAnnotation || it is PyTypeAliasStatement }) return true
      if (isGenericSubscriptArgument()) return true
      return parents(false).filterIsInstance<PyTypeParameter>().firstOrNull()
        ?.let { typeParam ->
          PsiTreeUtil.isAncestor(typeParam.boundExpression, this, false) ||
          PsiTreeUtil.isAncestor(typeParam.defaultExpression, this, false)
        } ?: false
    }

    private fun PyReferenceExpression.isGenericSubscriptArgument(): Boolean {
      val subscription = parents(false).filterIsInstance<PySubscriptionExpression>().firstOrNull() ?: return false
      val flatIndex = PyPsiUtils.flattenParens(subscription.indexExpression) ?: return false
      when (flatIndex) {
        is PyTupleExpression -> if (parent !== flatIndex) return false
        else -> if (this !== flatIndex) return false
      }
      val context = TypeEvalContext.codeAnalysis(project, containingFile)
      val operandType = context.getType(subscription.operand)
      return operandType is PyClassType && operandType.isDefinition && PyTypingTypeProvider.isGeneric(operandType, context)
    }

    /**
     * Gets the promotion hint for `complex` type.
     * Returns " | float | int", " | float", " | int", or null depending on
     * what's already present in the containing union.
     */
    private fun PyReferenceExpression.getComplexPromotionHint(floatClass: PyClass, intClass: PyClass): String? {
      val unionMembers = getUnionSiblings()
      if (unionMembers.isEmpty()) {
        return FLOAT_INT_PROMOTION_HINT
      }

      val hasFloat = unionMembers.any { it.isReferenceToClass(floatClass) }
      val hasInt = unionMembers.any { it.isReferenceToClass(intClass) }

      return when {
        !hasFloat && !hasInt -> FLOAT_INT_PROMOTION_HINT
        hasFloat && !hasInt -> INT_PROMOTION_HINT
        !hasFloat && hasInt -> FLOAT_PROMOTION_HINT
        else -> null
      }
    }

    /**
     * Gets the promotion hint for `float` type.
     * Returns " | int" or null depending on what's already present in the containing union.
     */
    private fun PyReferenceExpression.getFloatPromotionHint(intClass: PyClass): String? {
      val unionMembers = getUnionSiblings()
      if (unionMembers.isEmpty()) {
        return INT_PROMOTION_HINT
      }

      val hasInt = unionMembers.any { it.isReferenceToClass(intClass) }
      return if (!hasInt) INT_PROMOTION_HINT else null
    }

    /**
     * Gets all sibling members in the same union as the given element.
     * This handles both `X | Y` syntax and `Union[X, Y]` syntax.
     */
    private fun PyReferenceExpression.getUnionSiblings(): List<PsiElement> {
      val parent = parent

      if (parent is PyBinaryExpression && parent.operator == PyTokenTypes.OR) {
        return parent.findTopLevelUnion().collectAllUnionMembers()
          .filter { it !== this }
      }

      if (parent is PyTupleExpression) {
        val subscription = parent.parent as? PySubscriptionExpression
        if (subscription != null) {
          val context = TypeEvalContext.codeAnalysis(project, containingFile)
          val resolvedNames = PyTypingTypeProvider.resolveToQualifiedNames(subscription.operand, context)
          if (PyTypingTypeProvider.UNION in resolvedNames) {
            return parent.elements.filter { it !== this }
          }
        }
      }

      return emptyList()
    }

    /**
     * Finds the top-level binary expression in a chain of | operators.
     */
    private fun PyBinaryExpression.findTopLevelUnion(): PyBinaryExpression =
      parents(true)
        .takeWhile { it is PyBinaryExpression && it.operator == PyTokenTypes.OR }
        .last() as PyBinaryExpression

    /**
     * Collects all members from a union binary expression tree.
     */
    private fun PsiElement.collectAllUnionMembers(): List<PsiElement> = when (this) {
      is PyBinaryExpression -> {
        val left = this.leftExpression
        val right = this.rightExpression
        if (left != null && right != null) {
          left.collectAllUnionMembers() + right.collectAllUnionMembers()
        }
        else {
          emptyList()
        }
      }
      else -> listOf(this)
    }

    private fun PsiElement.isReferenceToClass(pyClass: PyClass): Boolean {
      if (this !is PyReferenceExpression) return false
      val resolved = this.reference.resolve()
      return resolved == pyClass
    }
  }
}

private const val FLOAT_PROMOTION_HINT = " | float"
private const val INT_PROMOTION_HINT = " | int"
private const val FLOAT_INT_PROMOTION_HINT = "$FLOAT_PROMOTION_HINT$INT_PROMOTION_HINT"
