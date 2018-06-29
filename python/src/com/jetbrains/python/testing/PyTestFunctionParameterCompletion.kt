// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing

import com.intellij.codeInsight.completion.*
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.python.codeInsight.completion.PythonLookupElement
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyParameterList
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.testing.pyTestFixtures.PyTestFixtureAsParameterProvider
import com.jetbrains.python.testing.pyTestParametrized.PyTestParameterFromParametrizeProvider
import javax.swing.Icon


internal class PyTestFunctionParameter(val name: String, val icon: Icon? = null)

/**
 * Implement, inject into [completionProviders]
 */
internal interface PyTestFunctionParameterProvider {
  fun getArguments(function: PyFunction, evalContext: TypeEvalContext, module: Module): List<PyTestFunctionParameter>
}

private val completionProviders = arrayOf(PyTestFixtureAsParameterProvider, PyTestParameterFromParametrizeProvider)

/**
 * Contributes function argument names.
 * @see completionProviders
 */
class PyTestParameterCompletionContributor : CompletionContributor() {
  init {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement().inside(PyParameterList::class.java), PyTestFunctionArgumentCompletion)
  }
}

private object PyTestFunctionArgumentCompletion : CompletionProvider<CompletionParameters>() {
  override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext?, result: CompletionResultSet) {
    val pyFunction = PsiTreeUtil.getParentOfType(parameters.position, PyParameterList::class.java)?.containingFunction ?: return
    val module = ModuleUtilCore.findModuleForPsiElement(pyFunction) ?: return
    val typeEvalContext = TypeEvalContext.codeCompletion(pyFunction.project, pyFunction.containingFile)
    val usedParams = pyFunction.getParameters(typeEvalContext).mapNotNull { it.name }.toSet()
    completionProviders
      .flatMap { it.getArguments(pyFunction, typeEvalContext, module) }
      .filter { !usedParams.contains(it.name) }
      .forEach {
        result.addElement(PythonLookupElement(it.name, false, it.icon))
      }
  }
}
