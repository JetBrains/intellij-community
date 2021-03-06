package com.jetbrains.python.psi.impl.references.hasattr

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.*

class PyHasAttrVisitor(private val resolvedQualifier: PsiElement) : PyRecursiveElementVisitor() {
  val result = hashSetOf<String>()
  private var myPositive: Boolean = true

  override fun visitPyPrefixExpression(node: PyPrefixExpression) {
    if (node.operator === PyTokenTypes.NOT_KEYWORD) {
      myPositive = !myPositive
      super.visitPyPrefixExpression(node)
      myPositive = !myPositive
    }
    else {
      super.visitPyPrefixExpression(node)
    }
  }

  override fun visitPyBinaryExpression(node: PyBinaryExpression) {
    if (!node.isOperator(PyNames.AND) && !node.isOperator(PyNames.OR)) return
    super.visitPyBinaryExpression(node)
  }

  override fun visitPyCallExpression(node: PyCallExpression) {
    if (!myPositive) return
    if (!node.isCalleeText(PyNames.HAS_ATTR)) return
    if (node.arguments.size != 2) return
    val firstArg = node.getArgument(0, PyReferenceExpression::class.java) ?: return
    val attrName = node.getArgument(1, PyStringLiteralExpression::class.java) ?: return

    if (firstArg.reference.isReferenceTo(resolvedQualifier)) {
      val variant = attrName.stringValue
      if (StringUtil.isJavaIdentifier(variant)) {
        result.add(variant)
      }
    }
  }
}