// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing

import com.intellij.psi.*
import com.jetbrains.python.fixture.PythonCommonTestCase
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.resolve.ImportedResolveResult
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.testing.pyTestFixtures.*
import junit.framework.TestCase

class PyTestFixtureResolvingTest : PyTestCase() {

  companion object {
    const val TESTS_SUBDIR = "/testPytestFixtureResolving"
    const val STR_TYPE_NAME = "str"
    const val STR_TYPE_DICT = "dict[$STR_TYPE_NAME, $STR_TYPE_NAME]"
    const val INT_STR_UNION = "int | str"

    const val SIMPLE_TEST_DIR = "/testSimple"
    const val SIMPLE_TEST_CONFTEST_FIXTURE = "/test_conftest_fixture.py"
    const val SIMPLE_TEST_NONE_RESOLVE = "/test_none_resolve.py"
    const val SIMPLE_TEST_ASYNCIO_FIXTURE = "test_asyncio_fixture.py"

    const val CLASS_TEST_DIR = "/testClass"
    const val CLASS_TEST_TWO_PARENTS = "/test_two_parents.py"
    const val CLASS_TEST_WITH_OVERRIDING_FILE = "test_with_overriding.py"
    const val CLASS_TEST_WITH_OVERRIDING = "/$CLASS_TEST_WITH_OVERRIDING_FILE"
    const val CLASS_TEST_WITHOUT_OVERRIDING = "/test_without_overriding.py"
    const val CLASS_TEST_SUBDIR = "/new_dir/simple_test.py"
    const val CLASS_TEST_CLASS_A_FILE = "base_class_A.py"
    const val CLASS_TEST_CLASS_B_FILE = "base_class_B.py"

    const val IMPORT_TEST_DIR = "/testImport"
    const val IMPORT_TEST_AS = "/import_as_test.py"
    const val IMPORT_TEST = "/import_test.py"
    const val IMPORT_TEST_ANOTHER_FIXTURE_FILE = "another_fixture.py"

    const val COMPLEX_STRUCTURE_TEST_DIR_NAME = "testComplexStructure"
    const val COMPLEX_STRUCTURE_TEST_DIR = "/$COMPLEX_STRUCTURE_TEST_DIR_NAME"
    const val COMPLEX_STRUCTURE_TEST_ROOT_FILE = "test_root.py"
    const val COMPLEX_STRUCTURE_TEST_ROOT = "/$COMPLEX_STRUCTURE_TEST_ROOT_FILE"
    const val COMPLEX_STRUCTURE_TEST_DIR_WITH_CONFTEST_NAME = "dir_with_conftest"
    const val COMPLEX_STRUCTURE_TEST_NEW_CONFTEST = "/$COMPLEX_STRUCTURE_TEST_DIR_WITH_CONFTEST_NAME/test_new_conftest.py"
    const val COMPLEX_STRUCTURE_TEST_ROOT_CONFTEST = "/dir_without_conftest/test_root_conftest.py"

    const val RESERVED_FIXTURES_TEST_DIR = "/testReservedFixtures"
    const val RESERVED_FIXTURES_TEST_FILE = "reserved_fixtures.py"

    const val REQUEST_TEST_IN_FIXTURE = "/test_request_in_fixture.py"
    const val REQUEST_TEST_IN_TEST = "/test_request_in_test.py"
    const val REQUEST_USAGES_TEST = "${RESERVED_FIXTURES_TEST_DIR}/test_request_usages.py"

    const val USE_FIXTURES_TEST_DIR_NAME = "testPytestMarkUseFixtures"
    const val USE_FIXTURES_TEST_DIR = "/$USE_FIXTURES_TEST_DIR_NAME"
    const val TEST_USE_FIXTURES_DECORATED_CLASS = "/test_decorator_for_class.py"
    const val TEST_USE_FIXTURES_DECORATED_TEST = "/test_decorator_for_test.py"
    const val TEST_USE_FIXTURES_EXPRESSION = "/test_expression.py"

    const val IMPORTED_FIXTURE_IN_CONFTEST_DIR_NAME = "testImportedFixturesInConftest"
    const val IMPORTED_FIXTURE_IN_CONFTEST_DIR = "/$IMPORTED_FIXTURE_IN_CONFTEST_DIR_NAME"
    const val TEST_IMPORTED_FIXTURE_IN_CONFTEST = "/test_imported_fixtures_in_conftest.py"
    const val IMPORTED_FIXTURE_IN_CONFTEST_FOO_FIXTURES_DIR_NAME = "fixtures"
    const val IMPORTED_FIXTURE_IN_CONFTEST_FOO_FIXTURES = "foo_fixtures.py"

    const val PYTEST_PLUGINS_FIXTURES_DIR_NAME = "testPytestPluginsFixtures"
    const val PYTEST_PLUGINS_FIXTURES_DIR = "/$PYTEST_PLUGINS_FIXTURES_DIR_NAME"
    const val PYTEST_PLUGINS_FIXTURES_AS_LIST_DIR = "/pytest_plugins_as_list"
    const val PYTEST_PLUGINS_FIXTURES_AS_STR_DIR = "/pytest_plugins_as_str"
    const val PYTEST_PLUGINS_FIXTURES_AS_TUPLE_DIR = "/pytest_plugins_as_tuple"
    const val PYTEST_PLUGINS_FIXTURES_AS_REF_DIR = "/pytest_plugins_as_ref"
    const val PYTEST_PLUGINS_FIXTURES_NOT_PARSABLE_DIR = "/pytest_plugins_not_parsable"
    const val PYTEST_PLUGINS_FIXTURES = "fixtures"
    const val PYTEST_PLUGINS_FIXTURES_FIRST = "first.py"
    const val PYTEST_PLUGINS_FIXTURES_SECOND = "second.py"
    const val PYTEST_PLUGINS_FIXTURES_AS_LIST_FIRST_TEST = "/test_pytest_plugins_as_list_first.py"
    const val PYTEST_PLUGINS_FIXTURES_AS_LIST_SECOND_TEST = "/test_pytest_plugins_as_list_second.py"
    const val PYTEST_PLUGINS_FIXTURES_AS_TUPLE_FIRST_TEST = "/test_pytest_plugins_as_tuple_first.py"
    const val PYTEST_PLUGINS_FIXTURES_AS_TUPLE_SECOND_TEST = "/test_pytest_plugins_as_tuple_second.py"
    const val PYTEST_PLUGINS_FIXTURES_AS_STR_TEST = "/test_pytest_plugins_as_str.py"
    const val PYTEST_PLUGINS_FIXTURES_AS_REF_TEST = "/test_pytest_plugins_as_ref.py"
    const val PYTEST_PLUGINS_FIXTURES_NOT_PARSABLE_TEST = "/test_pytest_plugins_not_parsable.py"

    const val IMPORT_WITH_WILDCARD_DIR_NAME = "testImportWithWildcard"
    const val IMPORT_WITH_WILDCARD_DIR = "/$IMPORT_WITH_WILDCARD_DIR_NAME"
    const val IMPORT_WITH_WILDCARD_FROM_INIT_DIR = "/import_from_init"
    const val IMPORT_WITH_WILDCARD_FROM_FILE_DIR = "/import_from_file"
    const val IMPORT_WITH_WILDCARD_FIXTURES_DIR = "fixtures_dir"
    const val IMPORT_WITH_WILDCARD_FIXTURES_FILE = "fixtures.py"
    const val IMPORT_WITH_WILDCARD_TEST_FILE = "/test_one.py"

    private const val PY_COLLECTION_TYPE_DIR_NAME = "testPyCollectionType"
    const val ITERATED_DIR = "/$PY_COLLECTION_TYPE_DIR_NAME/test_iterated"
    const val TEST_GENERATOR = "/test_generator.py"
    const val TEST_ITERABLE = "/test_iterable.py"
    const val TEST_ITERATOR = "/test_iterator.py"

    const val ASYNC_DIR = "/$PY_COLLECTION_TYPE_DIR_NAME/test_async"
    const val TEST_ASYNC_FUNCTION = "/test_async_function.py"
    const val TEST_ASYNC_GENERATOR = "/test_async_generator.py"
    const val TEST_ASYNC_ITERABLE = "/test_async_iterable.py"
    const val TEST_ASYNC_ITERATOR = "/test_async_iterator.py"

    const val PARAMETRIZED_DIR = "/testParameters"
    const val TEST_PARAMETER_TYPES = "/test_parameter_types.py"

  }

  override fun getTestDataPath() = super.getTestDataPath() + TESTS_SUBDIR

  override fun setUp() {
    super.setUp()
    TestRunnerService.getInstance(myFixture.module).selectedFactory =
      PythonTestConfigurationType.getInstance().pyTestFactory
    myFixture.copyDirectoryToProject("", "")
  }

  private fun getCaretReference(dirName: String, fileName: String): PsiReference? {
    val psiFile = myFixture.configureByFile(dirName + fileName)
    return psiFile?.findReferenceAt(myFixture.caretOffset)
  }

  private fun getResolve(dirName: String, fileName: String): PsiElement? {
    return getCaretReference(dirName, fileName)?.resolve()
  }

  private fun getImportMultiResolve(fileName: String): Array<ResolveResult?> {
    val reference = getCaretReference(IMPORT_TEST_DIR, fileName)
    PythonCommonTestCase.assertInstanceOf(reference, PsiPolyVariantReference::class.java)
    return (reference as PsiPolyVariantReference).multiResolve(false)
  }

  private fun getCaretElement(dirName: String, fileName: String): PsiElement? {
    val psiFile = myFixture.configureByFile(dirName + fileName)
    return psiFile.findElementAt(myFixture.caretOffset)
  }

  private fun assertCorrectType(dirName: String, fileName: String, typeName: String = STR_TYPE_NAME) {
    val resolve = getResolve(dirName, fileName) as PyNamedParameter
    val element = getCaretElement(dirName, fileName)
    TestCase.assertNotNull(element)
    val context = TypeEvalContext.codeAnalysis(element!!.project, element.containingFile)
    assertType(typeName, resolve, context)
  }

  private fun assertCorrectFile(dirName: String, fileName: String, expectedFileName: String?, expectedDirName: String? = null) {
    val result = getResolve(dirName, fileName)
    TestCase.assertEquals(expectedFileName, result?.containingFile?.name)
    expectedDirName?.let {
      TestCase.assertEquals(expectedDirName, result?.containingFile?.containingDirectory?.name)
    }
  }

  private fun assertCorrectImportResolve(fileName: String) {
    val results = getImportMultiResolve(fileName)
    TestCase.assertEquals(2, results.size)
    var hasImportedFixtureStatement = false
    for (result in results) {
      if (result is ImportedResolveResult) {
        hasImportedFixtureStatement = true
      }
      else {
        TestCase.assertEquals(IMPORT_TEST_ANOTHER_FIXTURE_FILE, result?.element?.containingFile?.name)
      }
    }
    TestCase.assertTrue(hasImportedFixtureStatement)
  }

  private fun getReservedFixturesFiles(fixturesSet: Set<String>): Set<String> {
    val result = mutableSetOf<String>()
    for (fixture in fixturesSet) {
      result.add("/test_${fixture}_fixture.py")
    }
    return result
  }

  private fun getReservedOverrideFixturesFiles(fixturesSet: Set<String>): Set<String> {
    val result = mutableSetOf<String>()
    for (fixture in fixturesSet) {
      result.add("test_override_${fixture}_fixture.py")
    }
    return result
  }

  fun testSimpleFixtureFromConftest() {
    assertCorrectFile(SIMPLE_TEST_DIR, SIMPLE_TEST_CONFTEST_FIXTURE, CONFTEST_PY)
  }

  fun testSimpleNotResolve() {
    assertCorrectFile(SIMPLE_TEST_DIR, SIMPLE_TEST_NONE_RESOLVE, null)
  }

  fun testSimpleAsyncioFixture() {
    assertCorrectFile(SIMPLE_TEST_DIR, "/$SIMPLE_TEST_ASYNCIO_FIXTURE", SIMPLE_TEST_ASYNCIO_FIXTURE)
  }

  fun testClassWithTwoParents() {
    assertCorrectFile(CLASS_TEST_DIR, CLASS_TEST_TWO_PARENTS, CLASS_TEST_CLASS_B_FILE)
  }

  fun testClassWithOverriding() {
    assertCorrectFile(CLASS_TEST_DIR, CLASS_TEST_WITH_OVERRIDING, CLASS_TEST_WITH_OVERRIDING_FILE)
  }

  fun testClassWithoutOverriding() {
    assertCorrectFile(CLASS_TEST_DIR, CLASS_TEST_WITHOUT_OVERRIDING, CLASS_TEST_CLASS_A_FILE)
  }

  fun testClassConftestFixture() {
    assertCorrectFile(CLASS_TEST_DIR, CLASS_TEST_SUBDIR, CONFTEST_PY)
  }

  fun testImportFixture() {
    assertCorrectImportResolve(IMPORT_TEST)
  }

  fun testImportFixtureAs() {
    assertCorrectImportResolve(IMPORT_TEST_AS)
  }

  fun testComplexStructureRoot() {
    assertCorrectFile(COMPLEX_STRUCTURE_TEST_DIR, COMPLEX_STRUCTURE_TEST_ROOT, COMPLEX_STRUCTURE_TEST_ROOT_FILE, COMPLEX_STRUCTURE_TEST_DIR_NAME)
  }

  fun testComplexStructureNewConftest() {
    assertCorrectFile(COMPLEX_STRUCTURE_TEST_DIR, COMPLEX_STRUCTURE_TEST_NEW_CONFTEST, CONFTEST_PY, COMPLEX_STRUCTURE_TEST_DIR_WITH_CONFTEST_NAME)
  }

  fun testComplexStructureRootConftest() {
    assertCorrectFile(COMPLEX_STRUCTURE_TEST_DIR, COMPLEX_STRUCTURE_TEST_ROOT_CONFTEST, CONFTEST_PY, COMPLEX_STRUCTURE_TEST_DIR_NAME)
  }

  fun testRequestInFixture() {
    val fixtureReference = getCaretReference(RESERVED_FIXTURES_TEST_DIR, REQUEST_TEST_IN_FIXTURE) as? PyTestFixtureReference
    TestCase.assertNotNull(fixtureReference)
    assertEquals(REQUEST_FIXTURE, fixtureReference?.element?.text)
  }

  fun testRequestInTest() {
    val fixtureReference = getCaretReference(RESERVED_FIXTURES_TEST_DIR, REQUEST_TEST_IN_TEST) as? PyTestFixtureReference
    TestCase.assertNull(fixtureReference)
  }

  fun testRequestUsages() {
    val usages = myFixture.testFindUsages(REQUEST_USAGES_TEST)
    assertEquals(0, usages.size)
  }

  fun testReservedFixtures() {
    for (file in getReservedFixturesFiles(reservedFixturesSet)) {
      assertCorrectFile(RESERVED_FIXTURES_TEST_DIR, "/${file}", RESERVED_FIXTURES_TEST_FILE, _PYTEST_DIR)
    }
  }

  fun testReservedOverrideFixtures() {
    val expectedDirName = RESERVED_FIXTURES_TEST_DIR.substring(1)
    for (file in getReservedOverrideFixturesFiles(reservedFixturesSet + reservedFixtureClassSet)) {
      assertCorrectFile(RESERVED_FIXTURES_TEST_DIR, "/${file}", file, expectedDirName)
    }
  }

  fun testReservedFixtureClasses() {
    for (fixture in reservedFixtureClassSet) {
      val fixtureReference = getCaretReference(RESERVED_FIXTURES_TEST_DIR, "/test_${fixture}_fixture.py") as? PyTestFixtureReference
      TestCase.assertNotNull(fixtureReference)
      assertEquals(fixture, fixtureReference?.element?.text)
    }
  }

  fun testUseFixturesDecoratedClass() {
    assertCorrectFile(USE_FIXTURES_TEST_DIR, TEST_USE_FIXTURES_DECORATED_CLASS, CONFTEST_PY, USE_FIXTURES_TEST_DIR_NAME)
  }

  fun testUseFixturesDecoratedTest() {
    assertCorrectFile(USE_FIXTURES_TEST_DIR, TEST_USE_FIXTURES_DECORATED_TEST, CONFTEST_PY, USE_FIXTURES_TEST_DIR_NAME)
  }

  fun testUseFixturesExpression() {
    assertCorrectFile(USE_FIXTURES_TEST_DIR, TEST_USE_FIXTURES_EXPRESSION, CONFTEST_PY, USE_FIXTURES_TEST_DIR_NAME)
  }

  fun testImportedFixtureInConftest() {
    assertCorrectFile(IMPORTED_FIXTURE_IN_CONFTEST_DIR, TEST_IMPORTED_FIXTURE_IN_CONFTEST, IMPORTED_FIXTURE_IN_CONFTEST_FOO_FIXTURES, IMPORTED_FIXTURE_IN_CONFTEST_FOO_FIXTURES_DIR_NAME)
  }

  fun testPytestPluginsFixtureAsList() {
    val testDir = PYTEST_PLUGINS_FIXTURES_DIR + PYTEST_PLUGINS_FIXTURES_AS_LIST_DIR
    assertCorrectFile(testDir, PYTEST_PLUGINS_FIXTURES_AS_LIST_FIRST_TEST, PYTEST_PLUGINS_FIXTURES_FIRST, PYTEST_PLUGINS_FIXTURES)
    assertCorrectFile(testDir, PYTEST_PLUGINS_FIXTURES_AS_LIST_SECOND_TEST, PYTEST_PLUGINS_FIXTURES_SECOND, PYTEST_PLUGINS_FIXTURES)
  }

  fun testPytestPluginsFixtureAsTuple() {
    val testDir = PYTEST_PLUGINS_FIXTURES_DIR + PYTEST_PLUGINS_FIXTURES_AS_TUPLE_DIR
    assertCorrectFile(testDir, PYTEST_PLUGINS_FIXTURES_AS_TUPLE_FIRST_TEST, PYTEST_PLUGINS_FIXTURES_FIRST, PYTEST_PLUGINS_FIXTURES)
    assertCorrectFile(testDir, PYTEST_PLUGINS_FIXTURES_AS_TUPLE_SECOND_TEST, PYTEST_PLUGINS_FIXTURES_SECOND, PYTEST_PLUGINS_FIXTURES)
  }

  fun testPytestPluginsFixtureAsStr() {
    val testDir = PYTEST_PLUGINS_FIXTURES_DIR + PYTEST_PLUGINS_FIXTURES_AS_STR_DIR
    assertCorrectFile(testDir, PYTEST_PLUGINS_FIXTURES_AS_STR_TEST, PYTEST_PLUGINS_FIXTURES_FIRST, PYTEST_PLUGINS_FIXTURES)
  }

  fun testPytestPluginsFixtureAsReferences() {
    val testDir = PYTEST_PLUGINS_FIXTURES_DIR + PYTEST_PLUGINS_FIXTURES_AS_REF_DIR
    assertCorrectFile(testDir, PYTEST_PLUGINS_FIXTURES_AS_REF_TEST, PYTEST_PLUGINS_FIXTURES_FIRST, PYTEST_PLUGINS_FIXTURES)
  }

  fun testPytestPluginsFixtureNotParsable() {
    val testDir = PYTEST_PLUGINS_FIXTURES_DIR + PYTEST_PLUGINS_FIXTURES_NOT_PARSABLE_DIR
    assertCorrectFile(testDir, PYTEST_PLUGINS_FIXTURES_NOT_PARSABLE_TEST, PYTEST_PLUGINS_FIXTURES_FIRST, PYTEST_PLUGINS_FIXTURES)
  }

  fun testImportWithWildCardFromInit() {
    val testDir = IMPORT_WITH_WILDCARD_DIR + IMPORT_WITH_WILDCARD_FROM_INIT_DIR
    assertCorrectFile(testDir, IMPORT_WITH_WILDCARD_TEST_FILE, IMPORT_WITH_WILDCARD_FIXTURES_FILE, IMPORT_WITH_WILDCARD_FIXTURES_DIR)
  }

  fun testImportWithWildCardFromFile() {
    val testDir = IMPORT_WITH_WILDCARD_DIR + IMPORT_WITH_WILDCARD_FROM_FILE_DIR
    assertCorrectFile(testDir, IMPORT_WITH_WILDCARD_TEST_FILE, IMPORT_WITH_WILDCARD_FIXTURES_FILE, IMPORT_WITH_WILDCARD_FIXTURES_DIR)
  }

  fun testGenerator() {
    assertCorrectType(ITERATED_DIR, TEST_GENERATOR, STR_TYPE_NAME)
  }

  fun testIterable() {
    assertCorrectType(ITERATED_DIR, TEST_ITERABLE)
  }

  fun testIterator() {
    assertCorrectType(ITERATED_DIR, TEST_ITERATOR)
  }

  fun testAsyncGenerator() {
    assertCorrectType(ASYNC_DIR, TEST_ASYNC_GENERATOR, STR_TYPE_NAME)
  }

  fun testAsyncIterable() {
    assertCorrectType(ASYNC_DIR, TEST_ASYNC_ITERABLE)
  }

  fun testAsyncIterator() {
    assertCorrectType(ASYNC_DIR, TEST_ASYNC_ITERATOR)
  }

  fun testAsyncFunction() {
    assertCorrectType(ASYNC_DIR, TEST_ASYNC_FUNCTION, STR_TYPE_DICT)
  }

  fun testNamedParameterTypes() {
    assertCorrectType(PARAMETRIZED_DIR, TEST_PARAMETER_TYPES, INT_STR_UNION)
  }
}