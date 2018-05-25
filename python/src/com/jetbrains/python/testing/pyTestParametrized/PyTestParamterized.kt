// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.pyTestParametrized

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ThreeState
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.testing.isTestElement

/**
 * @return Boolean is parameter provided to function by parametrized decorator
 */
fun PyNamedParameter.isParametrized(): Boolean = PsiTreeUtil.getParentOfType(this, PyFunction::class.java)
                                                   ?.getParametersFromGenerator()
                                                   ?.contains(name)
                                                 ?: false

/**
 * @return List<String> if test function decorated with parametrize -- return parameter names
 */
internal fun PyFunction.getParametersFromGenerator(): List<String> {
  val decoratorList = decoratorList ?: return emptyList()
  if (!isTestElement(this, ThreeState.NO, TypeEvalContext.codeAnalysis(project, containingFile))) {
    return emptyList()
  }
  val pyEvaluator = PyEvaluator()
  return decoratorList.decorators
    .filter { it.name == "parametrize" }
    .mapNotNull { pyEvaluator.evaluate(it.arguments.firstOrNull()) }
    .filterIsInstance(String::class.java)
    .flatMap { it.split(",") }
    .map(String::trim)
}