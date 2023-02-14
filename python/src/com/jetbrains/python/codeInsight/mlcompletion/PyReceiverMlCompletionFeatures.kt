// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.mlcompletion

import com.intellij.codeInsight.completion.ml.CompletionEnvironment
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.TypeEvalContext

object PyReceiverMlCompletionFeatures {
  val receiverNamesKey = Key<List<String>>("py.ml.completion.receiver.name")

  fun calculateReceiverElementInfo(environment: CompletionEnvironment, typeEvalContext: TypeEvalContext) {
    val position = environment.parameters.position
    val receivers = getReceivers(position, typeEvalContext)
    if (receivers.isEmpty()) return
    val names = receivers.mapNotNull { getNameOfReceiver(it) }
    environment.putUserData(receiverNamesKey, names)
  }

  private fun getNameOfReceiver(element: PsiElement): String? {
    return when (element) {
      is PyNamedParameter -> element.name
      is PyTargetExpression -> element.name
      else -> element.text
    }
  }

  private fun getReceivers(position: PsiElement, typeEvalContext: TypeEvalContext): List<PsiElement> {
    return when (val scope = PsiTreeUtil.getParentOfType(position, PyCallExpression::class.java, PyAssignmentStatement::class.java)) {
      is PyCallExpression -> getReceivers(position, scope, typeEvalContext)
      is PyAssignmentStatement -> getReceivers(position, scope)
      else -> emptyList()
    }
  }

  private fun getReceivers(position: PsiElement, call: PyCallExpression, typeEvalContext: TypeEvalContext): List<PsiElement> {
    val resolveContext = PyResolveContext.defaultContext(typeEvalContext)
    val mapArguments = call.multiMapArguments(resolveContext)
    if (mapArguments.isEmpty()) return emptyList()
    return mapArguments.mapNotNull { entry -> entry.mappedParameters[position.parent]?.parameter }
  }

  private fun getReceivers(position: PsiElement, assignment: PyAssignmentStatement): List<PsiElement> {
    val mapping = assignment.targetsToValuesMapping
    val result = mapping.find { it.second == position.parent }?.first
    return if (result != null) arrayListOf(result) else emptyList()
  }
}