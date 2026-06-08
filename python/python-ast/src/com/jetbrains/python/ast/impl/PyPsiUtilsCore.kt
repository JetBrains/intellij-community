// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast.impl

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.Condition
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.util.QualifiedName
import com.intellij.util.ArrayUtil
import com.jetbrains.python.ast.PyAstElement
import com.jetbrains.python.ast.PyAstExpression
import com.jetbrains.python.ast.PyAstParenthesizedExpression
import com.jetbrains.python.ast.PyAstQualifiedExpression
import com.jetbrains.python.ast.PyAstReferenceExpression
import com.jetbrains.python.ast.PyAstStringLiteralExpression
import com.jetbrains.python.psi.PyElementType
import org.jetbrains.annotations.ApiStatus
import java.util.LinkedList

@ApiStatus.Experimental
object PyPsiUtilsCore {
  @ApiStatus.Internal
  @JvmStatic
  fun <T : PyAstElement> nodesToPsi(nodes: Array<ASTNode>, array: Array<T>): Array<T> {
    val psiElements = ArrayUtil.newArray(ArrayUtil.getComponentType(array), nodes.size)
    for (i in nodes.indices) {
      psiElements[i] = nodes[i].psi as T
    }
    return psiElements
  }

  /**
   * Finds first sibling that is neither comment, nor whitespace after given element.
   * @param strict prohibit returning element itself
   */
  @JvmStatic
  fun getNextNonCommentSibling(start: PsiElement?, strict: Boolean): PsiElement? {
    if (!strict && !(start is PsiWhiteSpace || start is PsiComment)) {
      return start
    }
    return PsiTreeUtil.skipWhitespacesAndCommentsForward(start)
  }

  /**
   * Returns first child psi element with specified element type or `null` if no such element exists.
   * Semantically it's the same as `getChildByFilter(element, TokenSet.create(type), 0)`.
   *
   * @param element tree parent node
   * @param type    element type expected
   * @return child element described
   */
  @JvmStatic
  fun getFirstChildOfType(element: PsiElement, type: PyElementType): PsiElement? {
    val child = element.node.findChildByType(type)
    return child?.psi
  }

  /**
   * Returns child element in the psi tree
   *
   * @param filter  Types of expected child
   * @param number  number
   * @param element tree parent node
   * @return PsiElement - child psiElement
   */
  @JvmStatic
  fun getChildByFilter(element: PsiElement, filter: TokenSet, number: Int): PsiElement? {
    val node = element.node
    if (node != null) {
      val children = node.getChildren(filter)
      return if (0 <= number && number < children.size) children[number]!!.psi else null
    }
    return null
  }

  /**
   * Returns the first non-whitespace sibling following the given element but within its line boundaries.
   */
  @JvmStatic
  fun getNextNonWhitespaceSiblingOnSameLine(element: PsiElement): PsiElement? {
    var cur = element.nextSibling
    while (cur != null) {
      if (cur !is PsiWhiteSpace) {
        return cur
      }
      else if (cur.textContains('\n')) {
        break
      }
      cur = cur.nextSibling
    }
    return null
  }

  @JvmStatic
  fun strValue(expression: PyAstExpression?): String? {
    return if (expression is PyAstStringLiteralExpression) expression.stringValue else null
  }

  @JvmStatic
  fun asQualifiedName(expr: PyAstExpression?): QualifiedName? {
    return if (expr is PyAstQualifiedExpression) expr.asQualifiedName() else null
  }

  @JvmStatic
  fun asQualifiedName(expr: PyAstQualifiedExpression): QualifiedName? {
    val path: MutableList<String?> = LinkedList<String?>()
    val firstName = expr.referencedName
    if (firstName == null) {
      return null
    }
    path.add(firstName)
    var qualifier = expr.qualifier
    while (qualifier != null) {
      val qualifierReference = qualifier as? PyAstReferenceExpression
      if (qualifierReference == null) {
        return null
      }
      val qualifierName = qualifierReference.referencedName
      if (qualifierName == null) {
        return null
      }
      path.add(0, qualifierName)
      qualifier = qualifierReference.qualifier
    }
    return QualifiedName.fromComponents(path)
  }

  /**
   * Wrapper for [PsiUtilCore.ensureValid] that skips nulls
   */
  @JvmStatic
  fun assertValid(element: PsiElement?) {
    if (element == null) {
      return
    }
    PsiUtilCore.ensureValid(element)
  }

  @JvmStatic
  fun flattenParens(expr: PyAstExpression?): PyAstExpression? {
    var expr = expr
    while (expr is PyAstParenthesizedExpression) {
      expr = expr.containedExpression
    }
    return expr
  }

  /**
   * Returns ancestor of the element that is also direct child of the given super parent.
   *
   * @param element     element to start search from
   * @param superParent direct parent of the desired ancestor
   * @return described element or `null` if it doesn't exist
   */
  @JvmStatic
  fun getParentRightBefore(element: PsiElement, superParent: PsiElement): PsiElement? {
    return PsiTreeUtil.findFirstParent(element, false, Condition { element1: PsiElement? -> element1!!.parent === superParent })
  }
}
