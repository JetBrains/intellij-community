// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.pyTestFixtures

import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.PathUtil
import com.intellij.util.Processor
import com.intellij.util.ThreeState
import com.jetbrains.python.extensions.getSdk
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.stubs.PyDecoratorStub
import com.jetbrains.python.psi.stubs.PyDecoratorStubIndex
import com.jetbrains.python.psi.stubs.PyTestFixtureDecoratorStub
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.testing.PyTestFactory
import com.jetbrains.python.testing.TestRunnerService
import com.jetbrains.python.testing.autoDetectTests.PyAutoDetectionConfigurationFactory
import com.jetbrains.python.testing.isTestElement

private val PyFunction.asFixture: PyTestFixture?
  get() = decoratorList?.decorators?.firstOrNull { it.name in TEST_FIXTURE_DECORATOR_NAMES }?.let { createFixture(it) }

private fun PyDecoratorList.hasDecorator(names: Iterable<String>) = names.any { findDecorator(it) != null }

private fun PyElement.getFixtureName() = name ?: (this as? PyStringLiteralExpression)?.stringValue

/**
 * Needed to avoid using the too wide `PyExpression` type
 */
private sealed class PyParameterOrPyReferenceExpression(val expression: PyExpression) {
  class Parameter(expression: PyExpression) : PyParameterOrPyReferenceExpression(expression)
  class Reference(expression: PyExpression) : PyParameterOrPyReferenceExpression(expression)
}

internal fun getFixtureLink(element: PyElement, typeEvalContext: TypeEvalContext): NamedFixtureLink? {
  val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return null
  return when (element) {
    is PyNamedParameter -> getFixtureAsExpressionLink(PyParameterOrPyReferenceExpression.Parameter(element), typeEvalContext, module)
    is PyReferenceExpression -> getFixtureAsExpressionLink(PyParameterOrPyReferenceExpression.Reference(element), typeEvalContext, module)
    is PyStringLiteralExpression -> getFixtureAsStringLink(element, typeEvalContext, module)
    else -> null
  }
}

/**
 * If named parameter or reference expression has fixture (and import statement) -- return it
 */
private fun getFixtureAsExpressionLink(element: PyParameterOrPyReferenceExpression,
                                       typeEvalContext: TypeEvalContext,
                                       module: Module): NamedFixtureLink? {
  val pyExpression = element.expression
  val func = PsiTreeUtil.getParentOfType(pyExpression, PyFunction::class.java) ?: return null
  val fixtureCandidates = getFixtures(module, func, typeEvalContext).filter { o -> o.name == pyExpression.name }
  return module.basePath?.let { return findRightFixture(fixtureCandidates, func, pyExpression, typeEvalContext, it) }
}

/**
 * If string literal has fixture (and import statement) -- return it
 */
private fun getFixtureAsStringLink(element: PyStringLiteralExpression,
                                   typeEvalContext: TypeEvalContext,
                                   module: Module): NamedFixtureLink? {
  val fixtureCandidates = getModuleFixtures(module).filter { o -> o.name == element.stringValue }
  return module.basePath?.let { return findRightFixture(fixtureCandidates, null, element, typeEvalContext, it) }
}

data class NamedFixtureLink(val fixture: PyTestFixture, val importElement: PyElement?)

private fun PyTestFixture.getContainingFile(): PsiFile? = function?.containingFile

/**
 * Check if a fixture is in the "conftest.py" file in the given directory
 */
private fun PyTestFixture.isInConftestInDir(directory: PsiDirectory): Boolean {
  getContainingFile()?.let { return it.containingDirectory == directory && it.name == CONFTEST_PY } ?: return false
}

/**
 * Searching for the right fixture in
 * 1. [func] containing class and parent classes
 * 2. [func] containing file
 * 3. Import statements in fixture file
 * 4. "conftest.py" files in parent directories
 * 5. Import statements in "conftest.py" file
 * 6. Reserved fixtures in "_pytest" dir
 *
 * [fixtureCandidates] All pytest fixtures in a project that could be used by [func].forWhat
 * [func] PyFunction using [pyFixtureElement]
 * [pyFixtureElement] Fixture provided as PyNamedParameter or PyStringLiteralExpression
 * [projectPath] Project directory path
 *
 * @return Fixture and import element if fixture was imported or null
 */
private fun findRightFixture(fixtureCandidates: List<PyTestFixture>,
                             func: PyFunction?,
                             pyFixtureElement: PyElement,
                             typeEvalContext: TypeEvalContext,
                             projectPath: String): NamedFixtureLink? {
  val elementName = pyFixtureElement.getFixtureName() ?: return null

  // request fixture
  if (elementName == REQUEST_FIXTURE) {
    return if (func?.isFixture() == true)
      NamedFixtureLink(PyTestFixture(null, null, REQUEST_FIXTURE), null)
    else null
  }

  val currentFile: PsiFile = pyFixtureElement.containingFile

  // search in classes
  if (!fixtureCandidates.isEmpty()) {
    val containingClass = if (pyFixtureElement is PyStringLiteralExpression) {
      PsiTreeUtil.getParentOfType<PyDecorator>(pyFixtureElement)?.target
    }
    else {
      func
    }?.containingClass
    containingClass?.let { pyClass ->
      pyClass.findMethodByName(elementName, true, typeEvalContext)?.let { classMethod ->
        fixtureCandidates.find { it.function == classMethod }?.let { return NamedFixtureLink(it, null) }
      }
    }

    // search in file
    fixtureCandidates.find { it.getContainingFile() == currentFile }?.let { return NamedFixtureLink(it, null) }
  }

  // search in import
  if (currentFile is PyFile) {
    getFixtureFromImports(currentFile, elementName, func, fixtureCandidates)?.let { return it }
  }

  // search in "conftest.py" in parents directories
  if (!fixtureCandidates.isEmpty()) {
    var currentDirectory = currentFile.containingDirectory
    while (currentDirectory != null && currentDirectory.virtualFile.path != projectPath) {
      searchInConftest(fixtureCandidates, currentDirectory, elementName, func)?.let { return it }

      currentDirectory.parentDirectory?.let { currentDirectory = it }
    }
    currentDirectory?.let {
      searchInConftest(fixtureCandidates, currentDirectory, elementName, func)?.let { return it }
    }
  }

  // search reserved fixture in "_pytest" dir
  if (!fixtureCandidates.isEmpty()) {
    fixtureCandidates.find { fixtureCandidate ->
      fixtureCandidate.function?.containingFile?.containingDirectory?.name == _PYTEST_DIR && elementName in reservedFixturesSet
    }?.let { return NamedFixtureLink(it, null) }
  }

  // search reserved fixture class in "_pytest" dir
  if (elementName in reservedFixtureClassSet) {
    return NamedFixtureLink(PyTestFixture(null, null, elementName), null)
  }
  return null
}

/**
 * Search fixture or imported fixture in 'constest.py' file
 */
private fun searchInConftest(fixtureCandidates: List<PyTestFixture>,
                             currentDirectory: PsiDirectory,
                             elementName: String,
                             func: PyFunction?): NamedFixtureLink? {
  fixtureCandidates.find { it.isInConftestInDir(currentDirectory) }?.let { return NamedFixtureLink(it, null) }

  // search in imports and 'pytest_plugins' in "conftest.py" file
  (currentDirectory.findFile(CONFTEST_PY) as? PyFile)?.let { pyFile ->
    getFixtureFromImports(pyFile, elementName, func, fixtureCandidates)?.let { return it }
    getFixtureFromPytestPlugins(pyFile, fixtureCandidates)?.let { return it }
  }
  return null
}

/**
 * Return fixture from import statements
 */
private fun getFixtureFromImports(targetFile: PyFile,
                                  elementName: String,
                                  func: PyFunction?,
                                  fixtureCandidates: List<PyTestFixture>): NamedFixtureLink? {
  findImportedFixtureInFile(targetFile, elementName, func, fixtureCandidates)?.let { return it }
  findImportedFixtureWithWildcard(targetFile, elementName, func, fixtureCandidates)?.let { return it }
  return null
}

/**
 * Find fixture from standard import statements
 *
 * `from module import some_fixture as sf` or
 * `import module.some_fixture`
 */
private fun findImportedFixtureInFile(targetFile: PyFile,
                                      elementName: String,
                                      func: PyFunction?,
                                      fixtureCandidates: List<PyTestFixture>): NamedFixtureLink? {
  val importedFixture = targetFile.findExportedName(elementName) as? PyImportElement
  val resolveImportElements = importedFixture?.multiResolve()?.map { it.element }
  if (importedFixture != null) {
    // if fixture is imported as `from module import some_fixture as sf`
    resolveImportElements?.firstOrNull { (it as? PyFunction)?.isFixture() == true }?.let { fixture ->
      return (fixture as? PyFunction)?.let {  NamedFixtureLink(PyTestFixture(func, fixture, fixture.name ?: ""), importedFixture) }
    }

    resolveImportElements?.let { list ->
      fixtureCandidates.find { fixture -> list.contains(fixture.function) }?.let {
        return NamedFixtureLink(it, importedFixture)
      }
    }
  }
  return null
}

/**
 * Find fixture from import statements with wildcard
 *
 * `from module import *`
 */
private fun findImportedFixtureWithWildcard(targetFile: PyFile,
                                            elementName: String,
                                            func: PyFunction?,
                                            fixtureCandidates: List<PyTestFixture>): NamedFixtureLink? {
  val starImportElements = targetFile.importBlock.filter { importStatement -> importStatement.children.any { it is PyStarImportElement } }
  if (starImportElements.isNotEmpty()) {
    // Map: containing file to fixture
    val fileToFixture = fixtureCandidates.mapNotNull { candidate -> (candidate.getContainingFile() as? PyFile)?.let { it to candidate } }.toMap()
    // Map: resolved source file to PyStarImportElement (needs for import reference)
    val sourceToImport = starImportElements.mapNotNull { elem ->
      val sourceFile = elem.children.firstOrNull { it is PyReferenceExpression }?.let { it.reference?.resolve() as? PyFile }
      val starImportElement = elem.children.firstOrNull { it is PyStarImportElement } as? PyStarImportElement
      if (sourceFile == null || starImportElement == null) {
        null
      } else {
        Pair(sourceFile, starImportElement)
      }
    }

    sourceToImport.forEach {( sourceFile, importElement) ->
      // if a source file is a required file
      fileToFixture[sourceFile]?.let { return NamedFixtureLink(it, importElement) }

      // if a source file is "__init__.py"
      if (sourceFile.name == "__init__.py") {
        findImportedFixtureInFile(sourceFile, elementName, func, fixtureCandidates)?.let {
          return NamedFixtureLink(it.fixture, importElement)
        }
      }
    }
  }
  return null
}
/**
 * Return fixture from pytest_plugins
 */
private fun getFixtureFromPytestPlugins(targetFile: PyFile, fixtureCandidates: List<PyTestFixture>): NamedFixtureLink? {

  val pyTestPluginsStatement = targetFile.statements.findLast { it is PyAssignmentStatement && it.isAssignmentTo("pytest_plugins") }
                                 as? PyAssignmentStatement ?: return null
  val assignedValue = pyTestPluginsStatement.assignedValue ?: return null // str or Sequence[str]

  val fixtures: List<PyExpression> = when (assignedValue) {
    is PyListLiteralExpression -> assignedValue.elements.toList()
    is PyStringLiteralExpression -> listOf(assignedValue)
    is PyQualifiedExpression -> listOf(getFixtureNameFromQualifiedName(assignedValue))
    is PyParenthesizedExpression -> assignedValue.children.find { it is PyTupleExpression }?.let { tuple ->
      (tuple as PyTupleExpression).elements.mapNotNull { resolve(it) }
    } ?: emptyList()
    else -> {
      // `pytest_plugins` is not parsable, most likely looks like `pytest_plugins = create_pytest_plugins()`
      // In that case it is better to return any suitable fixtureCandidate
      val candidate = fixtureCandidates.firstOrNull() ?: return null
      return NamedFixtureLink(candidate, null)
    }
  }

  if (fixtures.isEmpty()) return null

  val fixturesPaths = fixtures.map {
    val text = it.text
    text.subSequence(1, text.length - 1).toString().replace('.', '/')
  }

  val candidate = fixtureCandidates.find { fixtureCandidate ->
    var fixtureFilePath = fixtureCandidate.function?.containingFile?.virtualFile?.path ?: return@find false
    fixtureFilePath = PathUtil.toSystemIndependentName(fixtureFilePath).let { it.subSequence(0, it.length - 3).toString() }
    fixturesPaths.any { fixtureFilePath.endsWith(it) }
  } ?: return null

  return NamedFixtureLink(candidate, null)
}

private fun getFixtureNameFromQualifiedName(assignedValue: PyQualifiedExpression): PyStringLiteralExpression {
  val factory = PyElementGenerator.getInstance(assignedValue.project)
  val qualifiedName = assignedValue.asQualifiedName().toString().substringBeforeLast(".__name__", "")
  return factory.createStringLiteralFromString(qualifiedName)
}

/**
 * @return Boolean If named parameter has fixture or not
 */
fun PyNamedParameter.isFixture(typeEvalContext: TypeEvalContext) = getFixtureLink(this, typeEvalContext) != null


/**
 * @return Boolean is function decorated as fixture or marked so by EP
 */
internal fun PyFunction.isFixture() = decoratorList?.hasDecorator(TEST_FIXTURE_DECORATOR_NAMES) ?: false || isCustomFixture()


/**
 * [function] PyFunction fixture function
 * [resolveTarget] PyElement where this fixture is resolved
 * [name] String fixture name
 */
data class PyTestFixture(val function: PyFunction? = null, val resolveTarget: PyElement? = function, val name: String)


fun findDecoratorsByName(module: Module, names: Iterable<String>): Iterable<PyDecorator> =
  names.flatMap { name ->
    StubIndex.getElements(PyDecoratorStubIndex.KEY, name, module.project,
                          GlobalSearchScope.union(
                            arrayOf(module.moduleContentScope, GlobalSearchScope.moduleRuntimeScope(module, true))),
                          PyDecorator::class.java)
  }


internal fun resolve(expr: PyExpression): PyExpression? {
  when (expr) {
    is PyStringLiteralExpression -> return expr
    is PyQualifiedExpression -> return getFixtureNameFromQualifiedName(expr)
    }
  return null
}


private fun createFixture(decorator: PyDecorator): PyTestFixture? {
  val target = decorator.target ?: return null
  return (decorator.getTestFixtureName() ?: target.name)?.let { name ->
    PyTestFixture(target, target, name)
  }
}

private fun PyDecorator.getTestFixtureName(): String? {
  return when (@Suppress("UNCHECKED_CAST") val stub = (this as StubBasedPsiElementBase<PyDecoratorStub>).greenStub) {
    null -> getTestFixtureName(this)
    else -> stub.getCustomStub(PyTestFixtureDecoratorStub::class.java)?.name
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
    (findDecoratorsByName(module, TEST_FIXTURE_DECORATOR_NAMES)
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

private fun getModuleFixtures(module: Module): List<PyTestFixture> {
  return if (isPyTestEnabled(module)) {
    findDecoratorsByName(module, TEST_FIXTURE_DECORATOR_NAMES).mapNotNull { createFixture(it) }
  }
  else emptyList()
}

internal fun isPyTestEnabled(module: Module): Boolean {
  val factory = TestRunnerService.getInstance(module).selectedFactory
  // Must be true even if SDK is not set
  if (factory.id == PyTestFactory.id) return true

  val sdk = module.getSdk() ?: return false
  val factoryId = (if (factory is PyAutoDetectionConfigurationFactory) factory.getFactory(sdk, module.project) else factory).id
  return factoryId == PyTestFactory.id
}
