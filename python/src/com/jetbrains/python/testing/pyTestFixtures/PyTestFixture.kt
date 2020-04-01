// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.pyTestFixtures

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import com.intellij.util.ThreeState
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.stubs.PyDecoratorStubIndex
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.testing.PyTestFrameworkService
import com.jetbrains.python.testing.TestRunnerService
import com.jetbrains.python.testing.isTestElement

private val decoratorNames = arrayOf("pytest.fixture", "fixture")

private val PyFunction.asFixture: PyTestFixture?
  get() = decoratorList?.decorators?.firstOrNull { it.name in decoratorNames }?.let { createFixture(it) }

private fun PyDecoratorList.hasDecorator(vararg names: String) = names.any { findDecorator(it) != null }

/**
 * If named parameter has fixture -- return it
 */
internal fun getFixture(element: PyNamedParameter, typeEvalContext: TypeEvalContext): PyTestFixture? {
  val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return null
  val func = PsiTreeUtil.getParentOfType(element, PyFunction::class.java) ?: return null
  return getFixtures(module, func, typeEvalContext).firstOrNull { o -> o.name == element.name }
}

/**
 * @return Boolean If named parameter has fixture or not
 */
fun PyNamedParameter.isFixture(typeEvalContext: TypeEvalContext) = getFixture(this, typeEvalContext) != null


/**
 * @return Boolean is function decorated as fixture or marked so by EP
 */
internal fun PyFunction.isFixture() = decoratorList?.hasDecorator(*decoratorNames) ?: false || isCustomFixture()


/**
 * [function] PyFunction fixture function
 * [resolveTarget] PyElement where this fixture is resolved
 * [name] String fixture name
 */
data class PyTestFixture(val function: PyFunction? = null, val resolveTarget: PyElement? = function, val name: String)


fun findDecoratorsByName(module: Module, vararg names: String): Iterable<PyDecorator> =
  names.flatMap { name ->
    StubIndex.getElements(PyDecoratorStubIndex.KEY, name, module.project,
                          GlobalSearchScope.union(
                            arrayOf(module.moduleContentScope, GlobalSearchScope.moduleRuntimeScope(module, true))),
                          PyDecorator::class.java)
  }


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

private val pyTestName = PyTestFrameworkService.getSdkReadableNameByFramework(PyNames.PY_TEST)

/**
 * Gets list of fixtures suitable for certain function.
 *
 * [forWhat] function that you want to use fixtures with. Could be test or fixture itself.
 *
 * @return all pytest fixtures in project that could be used by [forWhat]
 */
internal fun getFixtures(module: Module, forWhat: PyFunction, typeEvalContext: TypeEvalContext): List<PyTestFixture> {
  // Fixtures could be used only by test functions or other fixtures.
  val fixture = forWhat.isFixture()
  val pyTestEnabled = isPyTestEnabled(module)
  val topLevelixtures = if (
    fixture ||
    (pyTestEnabled && isTestElement(forWhat, ThreeState.NO, typeEvalContext)) ||
    forWhat.isSubjectForFixture()
  ) {
    //Fixtures
    (findDecoratorsByName(module, *decoratorNames)
       .filter { it.target?.containingClass == null } //We need only top-level functions, class-based fixtures processed above
       .mapNotNull { createFixture(it) }
     + getCustomFixtures(typeEvalContext, forWhat))
      .filterNot { fixture && it.name == forWhat.name }.toList()  // Do not suggest fixture for itself
  }
  else emptyList()
  val forWhatClass = forWhat.containingClass
  if (forWhatClass == null) {
    return topLevelixtures // Class fixtures can't be used for top level functions
  }

  val classBasedFixtures = mutableListOf<PyTestFixture>()
  forWhatClass.visitMethods(Processor { func ->
    func.asFixture?.let { classBasedFixtures.add(it) }
    true
  }, true, typeEvalContext)
  return classBasedFixtures + topLevelixtures
}

internal fun isPyTestEnabled(module: Module) =
  TestRunnerService.getInstance(module).projectConfiguration == pyTestName


