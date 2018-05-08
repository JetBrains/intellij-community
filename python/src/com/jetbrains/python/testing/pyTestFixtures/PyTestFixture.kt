// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.pyTestFixtures

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ThreeState
import com.jetbrains.python.psi.PyDecorator
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.stubs.PyDecoratorStubIndex
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.testing.isTestElement

const val decoratorName = "pytest.fixture"

/**
 * If named parameter has fixture -- return it
 */
internal fun getFixture(element: PyNamedParameter, typeEvalContext: TypeEvalContext): PyFunction? {
  val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return null
  val func = PsiTreeUtil.getParentOfType(element, PyFunction::class.java) ?: return null
  if (!isTestElement(func, ThreeState.NO, typeEvalContext)) {
    return null
  }
  return getFixtures(module).firstOrNull { o -> o.name == element.name }
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
 * @return List<PyFunction> all py.test fixtures in project
 */
internal fun getFixtures(module: Module) =
  StubIndex.getElements(PyDecoratorStubIndex.KEY, decoratorName, module.project,
                               module.moduleContentScope,
                               PyDecorator::class.java).mapNotNull(PyDecorator::getTarget)

