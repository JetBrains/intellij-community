// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.pyTestFixtures

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import com.intellij.util.ThreeState
import com.jetbrains.extensions.getSdk
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.stubs.PyDecoratorStubIndex
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.testing.PyTestFactory
import com.jetbrains.python.testing.TestRunnerService
import com.jetbrains.python.testing.autoDetectTests.PyAutoDetectionConfigurationFactory
import com.jetbrains.python.testing.isTestElement

private val decoratorNames = arrayOf("pytest.fixture", "fixture")

private val PyFunction.asFixture: PyTestFixture?
  get() = decoratorList?.decorators?.firstOrNull { it.name in decoratorNames }?.let { createFixture(it) }

private fun PyDecoratorList.hasDecorator(vararg names: String) = names.any { findDecorator(it) != null }

/**
 * If named parameter has fixture (and import statement) -- return it
 */
internal fun getFixtureParamLink(element: PyNamedParameter, typeEvalContext: TypeEvalContext): NamedFixtureParameterLink? {
  val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return null
  val func = PsiTreeUtil.getParentOfType(element, PyFunction::class.java) ?: return null
  val fixtureCandidates = getFixtures(module, func, typeEvalContext).filter { o -> o.name == element.name }
  return module.basePath?.let { return findRightFixture(fixtureCandidates, func, element, typeEvalContext, it) }
}

data class NamedFixtureParameterLink(val fixture: PyTestFixture, val importElement: PyImportElement?)

private fun PyTestFixture.getContainingFile(): PsiFile? = function?.containingFile

/**
 * Check if fixture is in the "conftest.py" file in the given directory
 */
private fun PyTestFixture.isInConftestInDir(directory: PsiDirectory): Boolean {
  getContainingFile()?.let { return it.containingDirectory == directory && it.name == CONFTEST_PY } ?: return false
}

/**
 * Searching the right fixture in
 * 1. [func] containing class and parent classes
 * 2. [func] containing file
 * 3. import statements
 * 4. "conftest.py" files in parent directories
 *
 * [fixtureCandidates] All pytest fixtures in project that could be used by [func].forWhat
 * [func] PyFunction using [fixtureNamedParameter]
 * [fixtureNamedParameter] Fixture provided as PyNamedParameter
 * [projectPath] Project directory path
 *
 * @return Fixture and import element if fixture was imported or null
 */
private fun findRightFixture(fixtureCandidates: List<PyTestFixture>,
                             func: PyFunction,
                             fixtureNamedParameter: PyNamedParameter,
                             typeEvalContext: TypeEvalContext,
                             projectPath: String): NamedFixtureParameterLink? {
  // request fixture
  if (fixtureNamedParameter.name == REQUEST_FIXTURE) {
    return if (func.isFixture())
      NamedFixtureParameterLink(PyTestFixture(null, null, REQUEST_FIXTURE), null)
    else null
  }

  val currentFile: PsiFile = func.containingFile

  // search in classes
  if (!fixtureCandidates.isEmpty()) {
    func.containingClass?.let { pyClass ->
      pyClass.findMethodByName(fixtureNamedParameter.name, true, typeEvalContext)?.let { classMethod ->
        fixtureCandidates.find { it.function == classMethod }?.let { return NamedFixtureParameterLink(it, null) }
      }
    }

    // search in file
    fixtureCandidates.find { it.getContainingFile() == currentFile }?.let { return NamedFixtureParameterLink(it, null) }
  }

  // search in import
  if (currentFile is PyFile) {
    val importedFixture = currentFile.findExportedName(fixtureNamedParameter.name) as? PyImportElement
    val resolveImportElements = importedFixture?.multiResolve()?.map { it.element }
    if (importedFixture != null) {
      // if fixture is imported as `from module import some_fixture as sf`
      resolveImportElements?.filterIsInstance<PyFunction>()?.firstOrNull()?.let { fixture ->
        return NamedFixtureParameterLink(PyTestFixture(func, fixture, fixture.name ?: ""), importedFixture)
      }

      resolveImportElements?.let { list ->
        fixtureCandidates.find { fixture -> list.contains(fixture.function) }?.let {
          return NamedFixtureParameterLink(it, importedFixture)
        }
      }
    }
  }

  // search in "conftest.py" in parents directories
  if (!fixtureCandidates.isEmpty()) {
    var currentDirectory = currentFile.containingDirectory
    while (currentDirectory != null && currentDirectory.virtualFile.path != projectPath) {
      fixtureCandidates.find { it.isInConftestInDir(currentDirectory) }?.let { return NamedFixtureParameterLink(it, null) }
      currentDirectory.parentDirectory?.let { currentDirectory = it }
    }
    currentDirectory?.let {
      fixtureCandidates.find { it.isInConftestInDir(currentDirectory) }?.let { return NamedFixtureParameterLink(it, null) }
    }
  }

  // search reserved fixture in "_pytest" dir
  if (!fixtureCandidates.isEmpty()) {
    fixtureCandidates.find { fixtureCandidate ->
      fixtureCandidate.function?.containingFile?.containingDirectory?.name == _PYTEST_DIR && fixtureNamedParameter.name in reservedFixturesSet
    }?.let { return NamedFixtureParameterLink(it, null) }
  }

  // search reserved fixture class in "_pytest" dir
  if (fixtureNamedParameter.name in reservedFixtureClassSet) {
    fixtureNamedParameter.name?.let {
      return NamedFixtureParameterLink(PyTestFixture(null, null, it), null)
    }
  }
  return null
}

/**
 * @return Boolean If named parameter has fixture or not
 */
fun PyNamedParameter.isFixture(typeEvalContext: TypeEvalContext) = getFixtureParamLink(this, typeEvalContext) != null


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
  val topLevelFixtures = if (
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
    return topLevelFixtures // Class fixtures can't be used for top level functions
  }

  val classBasedFixtures = mutableListOf<PyTestFixture>()
  forWhatClass.visitMethods(Processor { func ->
    func.asFixture?.let { classBasedFixtures.add(it) }
    true
  }, true, typeEvalContext)
  return classBasedFixtures + topLevelFixtures
}

internal fun isPyTestEnabled(module: Module): Boolean {
  val factory = TestRunnerService.getInstance(module).selectedFactory
  // Must be true even if SDK is not set
  if (factory.id == PyTestFactory.id) return true

  val sdk = module.getSdk() ?: return false
  val factoryId = (if (factory is PyAutoDetectionConfigurationFactory) factory.getFactory(sdk) else factory).id
  return factoryId == PyTestFactory.id
}



