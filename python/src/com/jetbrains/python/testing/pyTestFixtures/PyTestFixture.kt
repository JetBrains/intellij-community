// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.pyTestFixtures

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ThreeState
import com.jetbrains.python.psi.PyDecorator
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.stubs.PyDecoratorStubIndex
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.testing.isTestElement

const val decoratorName = "pytest.fixture"

/**
 * If named parameter has fixture -- return it
 */
internal fun getFixture(element: PyNamedParameter, typeEvalContext: TypeEvalContext): PyTestFixture? {
  val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return null
  val func = PsiTreeUtil.getParentOfType(element, PyFunction::class.java) ?: return null
  if (!isTestElement(func, ThreeState.NO, typeEvalContext)) {
    return null
  }
  return getFixtures(module)
    .filter { o -> o.name == element.name }
    .sortedBy {
      ModuleUtilCore.findModuleForPsiElement(it.function) != module
    }
    .firstOrNull()
}

/**
 * @return Boolean If named parameter has fixture or not
 */
internal fun hasFixture(element: PyNamedParameter, typeEvalContext: TypeEvalContext) = getFixture(element, typeEvalContext) != null

/**
 * @return Boolean is function decorated as fixture
 */
internal fun PyFunction.isFixture() = decoratorList?.findDecorator(decoratorName) != null

/**
 * @property function PyFunction fixture function
 * @property resolveTarget PyElement where this fixture is resolved
 * @property name String fixture name
 */
internal data class PyTestFixture(val function: PyFunction, val resolveTarget: PyElement, val name: String)

private fun createFixture(decorator: PyDecorator): PyTestFixture? {
  val target = decorator.target ?: return null
  val nameValue = decorator.argumentList?.getKeywordArgument("name")?.valueExpression
  if (nameValue != null) {
    val name = PyEvaluator.evaluate(nameValue, String::class.java) ?: return null
    return PyTestFixture(target, nameValue, name)
  }
  else {
    val name = target.name ?: return null
    return PyTestFixture(target, target, name)
  }
}

/**
 * @return List<PyFunction> all py.test fixtures in project
 */
internal fun getFixtures(module: Module) =
  StubIndex.getElements(PyDecoratorStubIndex.KEY, decoratorName, module.project,
                        GlobalSearchScope.union(arrayOf(module.moduleContentScope, GlobalSearchScope.moduleRuntimeScope(module, true))),
                        PyDecorator::class.java)
    .mapNotNull { createFixture(it) }

