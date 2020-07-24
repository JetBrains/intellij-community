// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.suggested

import com.intellij.refactoring.suggested.SuggestedChangeSignatureData
import com.intellij.refactoring.suggested.SuggestedRefactoringExecution
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Parameter
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.impl.ParamHelper
import com.jetbrains.python.refactoring.changeSignature.PyChangeSignatureProcessor
import com.jetbrains.python.refactoring.changeSignature.PyParameterInfo

internal class PySuggestedRefactoringExecution(support: PySuggestedRefactoringSupport) : SuggestedRefactoringExecution(support) {

  override fun prepareChangeSignature(data: SuggestedChangeSignatureData): Any? = null

  override fun performChangeSignature(data: SuggestedChangeSignatureData, newParameterValues: List<NewParameterValue>, preparedData: Any?) {
    val newParameterValuesIterator = newParameterValues.iterator()

    val parameterInfo = data.newSignature.parameters.map { newParameter ->
      val oldParameter = data.oldSignature.parameterById(newParameter.id)

      val oldIndex = if (oldParameter == null) -1 else data.oldSignature.parameterIndex(oldParameter)

      val oldName = oldParameter?.name ?: ""
      val newName = newParameter.name

      val (defaultValue, useValueInSignature) = defaultValueInfo(newParameter, oldParameter, newParameterValuesIterator)

      PyParameterInfo(oldIndex, oldName, defaultValue, useValueInSignature).apply { name = newName }
    }

    val function = data.declaration as PyFunction
    PyChangeSignatureProcessor(function.project, function, data.newSignature.name, parameterInfo.toTypedArray()).run()
  }

  private fun defaultValueInfo(newParameter: Parameter,
                               oldParameter: Parameter?,
                               newParametersValues: Iterator<NewParameterValue>): Pair<String?, Boolean> {
    if (!ParamHelper.couldHaveDefaultValue(newParameter.name)) return null to false

    val newDefaultValueInSignature = PySuggestedRefactoringSupport.defaultValue(newParameter)

    val defaultValue = defaultValueForProcessor(
      newDefaultValueInSignature,
      oldParameter?.let { PySuggestedRefactoringSupport.defaultValue(it) },
      oldParameter == null,
      newParametersValues
    ) ?: return null to false

    return defaultValue to (newDefaultValueInSignature == defaultValue)
  }

  private fun defaultValueForProcessor(newDefaultValueInSignature: String?,
                                       oldDefaultValueInSignature: String?,
                                       added: Boolean,
                                       newParametersValues: Iterator<NewParameterValue>): String? {
    return if (added) {
      defaultValueOnCallSite(newParametersValues) ?: newDefaultValueInSignature
    }
    else if (newDefaultValueInSignature == null && oldDefaultValueInSignature != null) {
      defaultValueOnCallSite(newParametersValues) ?: oldDefaultValueInSignature
    }
    else {
      newDefaultValueInSignature ?: oldDefaultValueInSignature
    }
  }

  private fun defaultValueOnCallSite(newParametersValues: Iterator<NewParameterValue>): String? {
    val expression = newParametersValues.next() as? NewParameterValue.Expression ?: return null
    return expression.expression.text
  }
}