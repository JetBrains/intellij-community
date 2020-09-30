// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.suggested

import com.intellij.openapi.ui.ValidationInfo
import com.intellij.psi.PsiCodeFragment
import com.intellij.refactoring.suggested.*
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Parameter
import com.jetbrains.python.PyBundle
import com.jetbrains.python.psi.PyExpressionStatement
import com.jetbrains.python.psi.impl.ParamHelper
import com.jetbrains.python.refactoring.changeSignature.PyExpressionCodeFragment
import com.jetbrains.python.refactoring.suggested.PySuggestedRefactoringSupport.Companion.defaultValue
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

internal object PySuggestedRefactoringUI : SuggestedRefactoringUI() {

  override fun createSignaturePresentationBuilder(signature: SuggestedRefactoringSupport.Signature,
                                                  otherSignature: SuggestedRefactoringSupport.Signature,
                                                  isOldSignature: Boolean): SignaturePresentationBuilder {
    return PySignaturePresentationBuilder(signature, otherSignature, isOldSignature)
  }

  override fun extractNewParameterData(data: SuggestedChangeSignatureData): List<NewParameterData> {
    val project = data.declaration.project

    return data.newSignature.parameters
      .map { it to data.oldSignature.parameterById(it.id) }
      .filter { (parameter, oldParameter) ->
        if (oldParameter == null) {
          ParamHelper.couldHaveDefaultValue(parameter.name)
        }
        else {
          defaultValue(oldParameter) != null && defaultValue(parameter) == null
        }
      }
      .map { (parameter, oldParameter) ->
        val valueFragment = PyExpressionCodeFragment(project, parameter.name, "")
        val shouldHaveDefaultValue = shouldHaveDefaultValue(parameter, oldParameter)

        NewParameterData(
          parameter.name,
          valueFragment,
          false,
          placeholderText(shouldHaveDefaultValue),
          ParameterData(shouldHaveDefaultValue)
        )
      }
  }

  override fun validateValue(data: NewParameterData, component: JComponent?): ValidationInfo? {
    if (data.valueFragment.text.isNotBlank()) return null

    val shouldHaveDefaultValue = (data.additionalData as? ParameterData)?.shouldHaveDefaultValue
    return when {
      shouldHaveDefaultValue == null || !shouldHaveDefaultValue -> null
      else -> ValidationInfo(PyBundle.message("refactoring.change.signature.dialog.validation.default.missing"), component)
    }
  }

  override fun extractValue(fragment: PsiCodeFragment): SuggestedRefactoringExecution.NewParameterValue.Expression? {
    val expression = (fragment.firstChild as? PyExpressionStatement)?.expression ?: return null
    return SuggestedRefactoringExecution.NewParameterValue.Expression(expression)
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  private fun placeholderText(shouldHaveDefaultValue: Boolean): String? {
    return if (shouldHaveDefaultValue) PyBundle.message("refactoring.change.signature.suggested.callSite.value")
    else PyBundle.message("refactoring.change.signature.suggested.callSite.value.optional")
  }

  private fun shouldHaveDefaultValue(newParameter: Parameter, oldParameter: Parameter?): Boolean {
    return ParamHelper.couldHaveDefaultValue(newParameter.name) &&
           defaultValue(newParameter) == null &&
           oldParameter?.let { defaultValue(it) } == null
  }

  private class PySignaturePresentationBuilder(
    signature: SuggestedRefactoringSupport.Signature,
    otherSignature: SuggestedRefactoringSupport.Signature,
    isOldSignature: Boolean
  ) : SignaturePresentationBuilder(signature, otherSignature, isOldSignature) {

    override fun buildPresentation() {
      fragments += leaf(signature.name, otherSignature.name)

      buildParameterList { fragments, parameter, correspondingParameter ->
        fragments += leaf(parameter.name, correspondingParameter?.name ?: parameter.name)

        val defaultValuePart = defaultValuePartInSignature(parameter)
        if (defaultValuePart != null) {
          fragments += leaf(defaultValuePart, correspondingParameter?.let { defaultValuePartInSignature(it) })
        }
      }
    }

    private fun defaultValuePartInSignature(parameter: Parameter): String? {
      return ParamHelper.getDefaultValuePartInSignature(defaultValue(parameter), false)
    }
  }

  private data class ParameterData(val shouldHaveDefaultValue: Boolean) : NewParameterAdditionalData
}