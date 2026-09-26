package com.intellij.python.lsp.core.type

import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyCapturePattern
import com.jetbrains.python.psi.PyClassPattern
import com.jetbrains.python.psi.PyDecorator
import com.jetbrains.python.psi.PyDictLiteralExpression
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyGeneratorExpression
import com.jetbrains.python.psi.PyLambdaExpression
import com.jetbrains.python.psi.PyListLiteralExpression
import com.jetbrains.python.psi.PyLiteralPattern
import com.jetbrains.python.psi.PyMappingPattern
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyPrefixExpression
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PySequencePattern
import com.jetbrains.python.psi.PySetCompExpression
import com.jetbrains.python.psi.PySetLiteralExpression
import com.jetbrains.python.psi.PySubscriptionExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyTupleExpression
import com.jetbrains.python.psi.PyWildcardPattern
import com.jetbrains.python.psi.PyYieldExpression

class LspIsSupportedTypesVisitor : PyElementVisitor() {
  var isSupported = false
    private set

  override fun visitPyMappingPattern(node: PyMappingPattern) {
    isSupported = true
  }

  override fun visitPySequencePattern(node: PySequencePattern) {
    isSupported = true
  }

  override fun visitPyBinaryExpression(node: PyBinaryExpression) {
    isSupported = true
  }

  override fun visitPyCallExpression(node: PyCallExpression) {
    isSupported = true
  }

  override fun visitPyCapturePattern(node: PyCapturePattern) {
    isSupported = true
  }

  override fun visitPyClassPattern(node: PyClassPattern) {
    isSupported = true
  }

  override fun visitPyDecorator(node: PyDecorator) {
    isSupported = true
  }

  override fun visitPyDictLiteralExpression(node: PyDictLiteralExpression) {
    isSupported = true
  }

  override fun visitPyFunction(node: PyFunction) {
    isSupported = true
  }

  override fun visitPyLambdaExpression(node: PyLambdaExpression) {
    isSupported = true
  }

  override fun visitPyListLiteralExpression(node: PyListLiteralExpression) {
    isSupported = true
  }

  override fun visitPyLiteralPattern(node: PyLiteralPattern) {
    isSupported = true
  }

  override fun visitPyNamedParameter(node: PyNamedParameter) {
    isSupported = true
  }

  override fun visitPyTupleExpression(node: PyTupleExpression) {
    isSupported = true
  }

  override fun visitPyPrefixExpression(node: PyPrefixExpression) {
    isSupported = true
  }

  override fun visitPyReferenceExpression(node: PyReferenceExpression) {
    isSupported = true
  }

  override fun visitPySetCompExpression(node: PySetCompExpression) {
    isSupported = true
  }

  override fun visitPySetLiteralExpression(node: PySetLiteralExpression) {
    isSupported = true
  }

  override fun visitPySubscriptionExpression(node: PySubscriptionExpression) {
    isSupported = true
  }

  override fun visitPyTargetExpression(node: PyTargetExpression) {
    isSupported = true
  }

  override fun visitWildcardPattern(node: PyWildcardPattern) {
    isSupported = true
  }

  override fun visitPyYieldExpression(node: PyYieldExpression) {
    isSupported = true
  }

  override fun visitPyGeneratorExpression(node: PyGeneratorExpression) {
    isSupported = true
  }
}