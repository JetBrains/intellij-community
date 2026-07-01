package com.intellij.python.pyrefly.typeEngine

import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyDecorator
import com.jetbrains.python.psi.PyDictLiteralExpression
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyLambdaExpression
import com.jetbrains.python.psi.PyPrefixExpression
import com.jetbrains.python.psi.PySequenceExpression
import com.jetbrains.python.psi.PySubscriptionExpression
import com.jetbrains.python.psi.PyTupleExpression

class PyreflyOffsetDetectorVisitor : PyElementVisitor() {
  var offset: Int = -1
    private set

  override fun visitPyElement(node: PyElement) {
    offset = node.endOffset - 1
  }

  override fun visitPyFunction(node: PyFunction) {
    offset = node.nameNode?.startOffset ?: node.startOffset
  }

  override fun visitPyLambdaExpression(node: PyLambdaExpression) {
    offset = node.startOffset
  }

  override fun visitPyCallExpression(node: PyCallExpression) {
    offset = node.endOffset
  }

  override fun visitPyDecorator(node: PyDecorator) {
    offset = node.endOffset
  }

  override fun visitPySubscriptionExpression(node: PySubscriptionExpression) {
    offset = node.endOffset
  }

  override fun visitPyPrefixExpression(node: PyPrefixExpression) {
    offset = node.startOffset
  }

  override fun visitPySequenceExpression(node: PySequenceExpression) {
    offset = node.endOffset
  }

  override fun visitPyTupleExpression(node: PyTupleExpression) {
    // we need to hit a comma or space to reveal the whole tuple type
    offset = node.elements.firstOrNull()?.endOffset?.plus(1) ?: node.endOffset
  }

  override fun visitPyDictLiteralExpression(node: PyDictLiteralExpression) {
    offset = node.endOffset
  }

  override fun visitPyBinaryExpression(node: PyBinaryExpression) {
    offset = node.psiOperator?.startOffset ?: node.startOffset
  }
}