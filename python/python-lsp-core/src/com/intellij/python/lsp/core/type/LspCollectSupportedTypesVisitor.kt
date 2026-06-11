package com.intellij.python.lsp.core.type

import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyCapturePattern
import com.jetbrains.python.psi.PyClassPattern
import com.jetbrains.python.psi.PyDecorator
import com.jetbrains.python.psi.PyDictLiteralExpression
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyLambdaExpression
import com.jetbrains.python.psi.PyListLiteralExpression
import com.jetbrains.python.psi.PyLiteralPattern
import com.jetbrains.python.psi.PyMappingPattern
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyPrefixExpression
import com.jetbrains.python.psi.PyRecursiveElementVisitor
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PySequencePattern
import com.jetbrains.python.psi.PySetCompExpression
import com.jetbrains.python.psi.PySetLiteralExpression
import com.jetbrains.python.psi.PySubscriptionExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.PyWildcardPattern
import com.jetbrains.python.psi.PyYieldExpression
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class LspCollectSupportedTypesVisitor: PyRecursiveElementVisitor() {
  val result: MutableList<PyTypedElement> = mutableListOf<PyTypedElement>()

  override fun visitPyMappingPattern(node: PyMappingPattern) {
    result += node
    super.visitPyMappingPattern(node)
  }

  override fun visitPySequencePattern(node: PySequencePattern) {
    result += node
    super.visitPySequencePattern(node)
  }

  override fun visitPyBinaryExpression(node: PyBinaryExpression) {
    result += node
    super.visitPyBinaryExpression(node)
  }

  override fun visitPyCallExpression(node: PyCallExpression) {
    result += node
    super.visitPyCallExpression(node)
  }

  override fun visitPyCapturePattern(node: PyCapturePattern) {
    result += node
    super.visitPyCapturePattern(node)
  }

  override fun visitPyClassPattern(node: PyClassPattern) {
    result += node
    super.visitPyClassPattern(node)
  }

  override fun visitPyDecorator(node: PyDecorator) {
    result += node
    super.visitPyDecorator(node)
  }

  override fun visitPyDictLiteralExpression(node: PyDictLiteralExpression) {
    result += node
    super.visitPyDictLiteralExpression(node)
  }

  override fun visitPyFunction(node: PyFunction) {
    result += node
    super.visitPyFunction(node)
  }

  override fun visitPyLambdaExpression(node: PyLambdaExpression) {
    result += node
    super.visitPyLambdaExpression(node)
  }

  override fun visitPyListLiteralExpression(node: PyListLiteralExpression) {
    result += node
    super.visitPyListLiteralExpression(node)
  }

  override fun visitPyLiteralPattern(node: PyLiteralPattern) {
    result += node
    super.visitPyLiteralPattern(node)
  }

  override fun visitPyNamedParameter(node: PyNamedParameter) {
    result += node
    super.visitPyNamedParameter(node)
  }

  override fun visitPyPrefixExpression(node: PyPrefixExpression) {
    result += node
    super.visitPyPrefixExpression(node)
  }

  override fun visitPyReferenceExpression(node: PyReferenceExpression) {
    result += node
    super.visitPyReferenceExpression(node)
  }

  override fun visitPySetCompExpression(node: PySetCompExpression) {
    result += node
    super.visitPySetCompExpression(node)
  }

  override fun visitPySetLiteralExpression(node: PySetLiteralExpression) {
    result += node
    super.visitPySetLiteralExpression(node)
  }

  override fun visitPySubscriptionExpression(node: PySubscriptionExpression) {
    result += node
    super.visitPySubscriptionExpression(node)
  }
  override fun visitPyTargetExpression(node: PyTargetExpression) {
    result += node
    super.visitPyTargetExpression(node)
  }

  override fun visitWildcardPattern(node: PyWildcardPattern) {
    result += node
    super.visitWildcardPattern(node)
  }

  override fun visitPyYieldExpression(node: PyYieldExpression) {
    result += node
    super.visitPyYieldExpression(node)
  }
}