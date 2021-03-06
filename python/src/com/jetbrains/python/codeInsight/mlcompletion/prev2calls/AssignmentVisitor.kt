// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.mlcompletion.prev2calls

import com.intellij.codeInsight.completion.CompletionUtilCore
import com.intellij.psi.PsiElement
import com.jetbrains.python.codeInsight.mlcompletion.PyMlCompletionHelpers
import com.jetbrains.python.psi.*

class AssignmentVisitor(private val borderOffset: Int,
                        private val scope: PsiElement,
                        private val fullNames: MutableMap<String, String>) : PyRecursiveElementVisitor() {
  data class QualifierAndReference(val qualifier: String, val reference: String)

  val arrPrevCalls = ArrayList<QualifierAndReference>()

  override fun visitPyReferenceExpression(node: PyReferenceExpression) {
    super.visitPyReferenceExpression(node)
    if (node.textOffset > borderOffset) return

    val (resolvedExpression, resolvedPrefix) = getResolvedExpression(node)
    if (node.parent !is PyCallExpression &&
        CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED !in resolvedExpression &&
        ("." !in resolvedExpression || resolvedPrefix == resolvedExpression)) return

    val qualifier = resolvedExpression.substringBeforeLast(".", "")
    val reference = resolvedExpression.substringAfterLast(".")
    arrPrevCalls.add(QualifierAndReference(qualifier, reference))
  }

  override fun visitPyWithStatement(node: PyWithStatement) {
    if (node.textOffset > borderOffset) return
    node.withItems.filter { it.expression != null && it.target != null }.forEach {
      fullNames[it.target!!.text] = getResolvedExpression(it.expression).resolvedExpression
    }
    super.visitPyWithStatement(node)
  }

  override fun visitPyAssignmentStatement(node: PyAssignmentStatement) {
    if (node.textOffset > borderOffset) return
    super.visitPyAssignmentStatement(node)

    node.targetsToValuesMapping.forEach {
      val left = it.first
      val right = it.second
      if (left is PyTargetExpression) {
        val leftName = PyMlCompletionHelpers.getQualifiedComponents(left).joinToString(".")
        val rightName = getResolvedExpression(right).resolvedExpression
        if (rightName.isNotEmpty() && leftName.isNotEmpty()) {
          fullNames[leftName] = rightName
        }
      }
    }
  }

  data class ResolvedExpression(val resolvedExpression: String = "", val resolvedPrefix: String = "")
  private fun getResolvedExpression(node: PyExpression?): ResolvedExpression {
    if (node == null) return ResolvedExpression()

    val components = PyMlCompletionHelpers.getQualifiedComponents(node)
    for (i in components.indices) {
      val firstN = i + 1
      val prefix = components.take(firstN).joinToString(".")
      fullNames[prefix]?.let { resolvedPrefix ->
        val postfix =
          if (firstN < components.size)
            components.takeLast(components.size - firstN).joinToString(separator=".", prefix=".")
          else ""
        return ResolvedExpression("$resolvedPrefix$postfix", resolvedPrefix)
      }
    }

    return ResolvedExpression(components.joinToString("."))
  }

  override fun visitPyFunction(node: PyFunction) {
    if (node == scope) super.visitPyFunction(node)
  }

  override fun visitPyClass(node: PyClass) {
    if (node == scope) super.visitPyClass(node)
  }
}