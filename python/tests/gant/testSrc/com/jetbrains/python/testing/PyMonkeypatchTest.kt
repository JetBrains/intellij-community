// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing

import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Components
import com.intellij.idea.TestFor
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyExpressionStatement
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.testing.pyMock.PyMockPatchTargetReferenceSet

/**
 * Tests for `monkeypatch.setattr` / `monkeypatch.delattr` IDE support:
 * navigation, completion, and reference resolution.
 */
@TestFor(issues = ["PY-78622"])
@Subsystems.TestRunner
@Components.Unittest
@Layers.Functional
class PyMonkeypatchTest : PyTestCase() {

  companion object {
    const val TEST_DATA_SUBDIR = "/testing/pyMonkeypatch"
  }

  override fun getTestDataPath(): String = super.getTestDataPath() + TEST_DATA_SUBDIR

  override fun setUp() {
    super.setUp()
    TestRunnerService.getInstance(myFixture.module).selectedFactory =
      PythonTestConfigurationType.getInstance().pyTestFactory
    myFixture.copyDirectoryToProject("", "")
  }

  // --- Completion ---

  fun testCompletionFirstSegment() {
    myFixture.configureByFile("test_completion_first_segment.py")
    myFixture.completeBasic()
    val variants = myFixture.lookupElementStrings
    assertNotNull("Completion should provide variants", variants)
    assertTrue("Should suggest example_module", variants!!.contains("example_module"))
  }

  fun testCompletionSecondSegment() {
    myFixture.configureByFile("test_completion_second_segment.py")
    myFixture.completeBasic()
    val variants = myFixture.lookupElementStrings
    assertNotNull("Completion should provide variants", variants)
    assertTrue("Should suggest MyClass", variants!!.contains("MyClass"))
    assertTrue("Should suggest top_level_function", variants.contains("top_level_function"))
  }

  // --- Dotted string form: monkeypatch.setattr("module.Class.attr", value) ---

  fun testDottedNavigationToPackageResolvesToInit() {
    myFixture.configureByFile("test_package_navigation.py")
    val file = myFixture.file as PyFile
    val func = file.findTopLevelFunction("test_setattr_through_package")!!
    val strArg = findFirstStringArg(func)
    assertNotNull("Expected string literal", strArg)

    // "example_package.submodule.SubClass.sub_method" has 4 segments
    val refs = PyMockPatchTargetReferenceSet(strArg!!, false).createReferences()
    assertSize(4, refs)

    // First segment (package) should resolve to __init__.py, not the directory
    val packageResolved = refs[0].resolve()
    assertNotNull("example_package should resolve", packageResolved)
    assertInstanceOf(packageResolved, PyFile::class.java)
    assertEquals("__init__.py", (packageResolved as PyFile).name)

    // Second segment (submodule) should resolve to the .py file
    val submoduleResolved = refs[1].resolve()
    assertNotNull("submodule should resolve", submoduleResolved)
    assertInstanceOf(submoduleResolved, PyFile::class.java)
    assertEquals("submodule.py", (submoduleResolved as PyFile).name)

    // Last segment should resolve to the method
    val methodResolved = refs[3].resolve()
    assertNotNull("sub_method should resolve", methodResolved)
    assertInstanceOf(methodResolved, PyFunction::class.java)
    assertEquals("sub_method", (methodResolved as PyFunction).name)
  }

  fun testDottedNavigationToClass() {
    myFixture.configureByFile("test_setattr.py")
    val file = myFixture.file as PyFile
    val func = file.findTopLevelFunction("test_setattr_dotted_class")!!
    val strArg = findFirstStringArg(func)
    assertNotNull("Expected string literal", strArg)

    val refs = PyMockPatchTargetReferenceSet(strArg!!, false).createReferences()
    assertEquals("example_module.MyClass should produce 2 references", 2, refs.size)
    val resolved = refs[1].resolve()
    assertNotNull("MyClass should resolve", resolved)
    assertInstanceOf(resolved, PyClass::class.java)
    assertEquals("MyClass", (resolved as PyClass).name)
  }

  fun testDottedNavigationToMethod() {
    myFixture.configureByFile("test_setattr.py")
    val file = myFixture.file as PyFile
    val func = file.findTopLevelFunction("test_setattr_dotted_method")!!
    val strArg = findFirstStringArg(func)
    assertNotNull("Expected string literal", strArg)

    val refs = PyMockPatchTargetReferenceSet(strArg!!, false).createReferences()
    assertSize(3, refs)
    val resolved = refs[2].resolve()
    assertNotNull("my_method should resolve", resolved)
    assertInstanceOf(resolved, PyFunction::class.java)
    assertEquals("my_method", (resolved as PyFunction).name)
  }

  fun testDottedNavigationToFunction() {
    myFixture.configureByFile("test_setattr.py")
    val file = myFixture.file as PyFile
    val func = file.findTopLevelFunction("test_setattr_dotted_function")!!
    val strArg = findFirstStringArg(func)
    assertNotNull("Expected string literal", strArg)

    val refs = PyMockPatchTargetReferenceSet(strArg!!, false).createReferences()
    assertSize(2, refs)
    val resolved = refs[1].resolve()
    assertNotNull("top_level_function should resolve", resolved)
    assertInstanceOf(resolved, PyFunction::class.java)
  }

  fun testDottedUnresolvedIsHard() {
    myFixture.configureByFile("test_setattr.py")
    val file = myFixture.file as PyFile
    val func = file.findTopLevelFunction("test_setattr_dotted_unresolved")!!
    val strArg = findFirstStringArg(func)
    assertNotNull("Expected string literal", strArg)

    val refs = PyMockPatchTargetReferenceSet(strArg!!, false).createReferences()
    val lastRef = refs.last()
    assertFalse("Reference should be hard", lastRef.isSoft)
    assertNull("DoesNotExist should not resolve", lastRef.resolve())
  }

  // --- Object + attribute form: monkeypatch.setattr(obj, "attr", value) ---

  fun testObjectFormNavigationToMethod() {
    myFixture.configureByFile("test_setattr.py")
    val file = myFixture.file as PyFile
    val func = file.findTopLevelFunction("test_setattr_object_method")!!
    val strArg = findSecondStringArg(func)
    assertNotNull("Expected string literal as second arg", strArg)

    val refs = strArg!!.references
    assertTrue("Should have references", refs.isNotEmpty())
    val resolved = refs.last().resolve()
    assertNotNull("my_method should resolve", resolved)
    assertInstanceOf(resolved, PyFunction::class.java)
    assertEquals("my_method", (resolved as PyFunction).name)
  }

  fun testObjectFormNavigationToAttr() {
    myFixture.configureByFile("test_setattr.py")
    val file = myFixture.file as PyFile
    val func = file.findTopLevelFunction("test_setattr_object_attr")!!
    val strArg = findSecondStringArg(func)
    assertNotNull("Expected string literal as second arg", strArg)

    val refs = strArg!!.references
    assertTrue("Should have references", refs.isNotEmpty())
    val resolved = refs.last().resolve()
    assertNotNull("class_attr should resolve", resolved)
  }

  fun testObjectFormUnresolved() {
    myFixture.configureByFile("test_setattr.py")
    val file = myFixture.file as PyFile
    val func = file.findTopLevelFunction("test_setattr_object_unresolved")!!
    val strArg = findSecondStringArg(func)
    assertNotNull("Expected string literal as second arg", strArg)

    val refs = strArg!!.references
    assertTrue("Should have references", refs.isNotEmpty())
    assertNull("does_not_exist should not resolve", refs.last().resolve())
  }

  fun testObjectFormModuleTarget() {
    myFixture.configureByFile("test_setattr.py")
    val file = myFixture.file as PyFile
    val func = file.findTopLevelFunction("test_setattr_module_function")!!
    val strArg = findSecondStringArg(func)
    assertNotNull("Expected string literal as second arg", strArg)

    val refs = strArg!!.references
    assertTrue("Should have references", refs.isNotEmpty())
    val resolved = refs.last().resolve()
    assertNotNull("top_level_function should resolve on module", resolved)
    assertInstanceOf(resolved, PyFunction::class.java)
  }

  // --- delattr ---

  fun testDelattrDottedForm() {
    myFixture.configureByFile("test_setattr.py")
    val file = myFixture.file as PyFile
    val func = file.findTopLevelFunction("test_delattr_dotted")!!
    val strArg = findFirstStringArg(func)
    assertNotNull("Expected string literal", strArg)

    val refs = PyMockPatchTargetReferenceSet(strArg!!, false).createReferences()
    assertSize(3, refs)
    assertNotNull("class_attr should resolve", refs[2].resolve())
  }

  fun testDelattrObjectForm() {
    myFixture.configureByFile("test_setattr.py")
    val file = myFixture.file as PyFile
    val func = file.findTopLevelFunction("test_delattr_object")!!
    val strArg = findSecondStringArg(func)
    assertNotNull("Expected string literal as second arg", strArg)

    val refs = strArg!!.references
    assertTrue("Should have references", refs.isNotEmpty())
    assertNotNull("class_attr should resolve", refs.last().resolve())
  }

  // --- Non-test function should not get references ---

  fun testNonTestFunctionNoReferences() {
    myFixture.configureByFile("test_non_test_function.py")
    val file = myFixture.file as PyFile
    val nonTestFunc = file.findTopLevelFunction("bar")!!
    val strArg = findFirstStringArg(nonTestFunc)
    assertNotNull("Expected string literal", strArg)

    // "example_module.MyClass" in a non-test function should NOT get monkeypatch references
    val refs = strArg!!.references.filter { it.javaClass.name.contains("Monkeypatch") || it.javaClass.name.contains("Mock") }
    assertTrue("Non-test function should not get monkeypatch references", refs.isEmpty())
  }

  fun testNonTestFunctionObjectFormNoReferences() {
    myFixture.configureByFile("test_non_test_function.py")
    val file = myFixture.file as PyFile
    val nonTestFunc = file.findTopLevelFunction("bar")!!
    // Find the second call: monkeypatch.setattr(MyClass, "my_method", ...)
    val calls = nonTestFunc.statementList.statements
      .filterIsInstance<com.jetbrains.python.psi.PyExpressionStatement>()
      .mapNotNull { it.expression as? PyCallExpression }
    val strArg = calls.getOrNull(1)?.argumentList?.arguments?.getOrNull(1) as? PyStringLiteralExpression
    assertNotNull("Expected string literal in second call", strArg)

    val refs = strArg!!.references.filter { it.javaClass.name.contains("Monkeypatch") || it.javaClass.name.contains("Mock") }
    assertTrue("Non-test function should not get monkeypatch references", refs.isEmpty())
  }

  fun testTestFunctionStillGetsReferences() {
    myFixture.configureByFile("test_non_test_function.py")
    val file = myFixture.file as PyFile
    val testFunc = file.findTopLevelFunction("test_foo")!!
    val strArg = findFirstStringArg(testFunc)
    assertNotNull("Expected string literal", strArg)

    val refs = PyMockPatchTargetReferenceSet(strArg!!, false).createReferences()
    assertTrue("Test function should still get references", refs.isNotEmpty())
  }

  // --- f-string arguments ---

  @TestFor(issues = ["PY-91732"])
  fun `test f-string with a placeholder is not reported as unresolved`() {
    myFixture.configureByText("test_fstring.py", """
      from example_module import MyClass

      _MODULE = "example_module"
      _NAME = "my_method"

      def test_foo(monkeypatch):
          monkeypatch.setattr(f"{_MODULE}.DoesNotExist", lambda: None)
          monkeypatch.setattr(MyClass, f"{_NAME}_does_not_exist", lambda self: None)
          monkeypatch.setattr("example_module.<error descr="Unresolved reference 'DoesNotExist'">DoesNotExist</error>", lambda: None)
    """.trimIndent())
    myFixture.enableInspections(PyUnresolvedReferencesInspection::class.java)
    myFixture.checkHighlighting(true, false, false)
  }

  // --- Targets whose type the code analysis context cannot infer ---

  @TestFor(issues = ["PY-91081"])
  fun `test attribute of an instance attribute inferred in another file is not reported`() {
    addClientModule()
    myFixture.configureByText("test_client.py", """
      from bla._client import Client, Reqs

      def test_foo(monkeypatch):
          client = Client()
          monkeypatch.setattr(client.reqs, "not_exists", lambda: None)
          monkeypatch.setattr(Reqs(), "<error descr="Unresolved reference 'does_not_exist'">does_not_exist</error>", lambda: None)
    """.trimIndent())
    myFixture.enableInspections(PyUnresolvedReferencesInspection::class.java)
    // The highlighting must not read the source of the other module
    (myFixture as CodeInsightTestFixtureImpl).setVirtualFileFilter { it.name == "_client.py" }
    myFixture.checkHighlighting(true, false, false)
  }

  @TestFor(issues = ["PY-91081"])
  fun `test completion lists the attributes of an instance attribute inferred in another file`() {
    addClientModule()
    myFixture.configureByText("test_client.py", """
      from bla._client import Client

      def test_foo(monkeypatch):
          client = Client()
          monkeypatch.setattr(client.reqs, "<caret>", lambda: None)
    """.trimIndent())
    myFixture.completeBasic()
    assertContainsElements(myFixture.lookupElementStrings.orEmpty(), "not_exists", "other")
  }

  @TestFor(issues = ["PY-91081"])
  fun `test attribute of an instance attribute inferred in another file resolves`() {
    addClientModule()
    myFixture.configureByText("test_client.py", """
      from bla._client import Client

      def test_foo(monkeypatch):
          client = Client()
          monkeypatch.setattr(client.reqs, "not_exists", lambda: None)
    """.trimIndent())
    val func = (myFixture.file as PyFile).findTopLevelFunction("test_foo")!!
    val call = func.statementList.statements.last() as PyExpressionStatement
    val strArg = (call.expression as PyCallExpression).arguments[1] as PyStringLiteralExpression
    val resolved = strArg.references.last().resolve()
    assertInstanceOf(resolved, PyFunction::class.java)
    assertEquals("Reqs", (resolved as PyFunction).containingClass?.name)
  }

  @TestFor(issues = ["PY-90524"])
  fun `test only a target that can stand for any class skips the check of the attribute`() {
    myFixture.configureByText("test_any.py", """
      from typing import Any

      def test_any(monkeypatch, a: Any):
          monkeypatch.setattr(a, "abc", None)

      def test_object(monkeypatch, o: object):
          monkeypatch.setattr(o, "abc", None)

      def test_class_of_any(monkeypatch, c: type[Any], t: type):
          monkeypatch.setattr(c, "abc", None)
          monkeypatch.setattr(t, "abc", None)

      def test_no_annotation(monkeypatch, p):
          monkeypatch.setattr(p, "abc", None)

      def test_class_of_int(monkeypatch, c: type[int]):
          monkeypatch.setattr(c, "<error descr="Unresolved reference 'abc'">abc</error>", None)

      def test_object_class(monkeypatch):
          monkeypatch.setattr(object, "<error descr="Unresolved reference 'abc'">abc</error>", None)
    """.trimIndent())
    myFixture.enableInspections(PyUnresolvedReferencesInspection::class.java)
    myFixture.checkHighlighting(true, false, false)
  }

  @TestFor(issues = ["PY-90524"])
  fun `test attribute of an instance of object resolves`() {
    myFixture.configureByText("test_object.py", """
      def test_object(monkeypatch, o: object):
          monkeypatch.setattr(o, "__repr__", None)
    """.trimIndent())
    val func = (myFixture.file as PyFile).findTopLevelFunction("test_object")!!
    val strArg = findSecondStringArg(func)!!
    val resolved = strArg.references.last().resolve()
    assertInstanceOf(resolved, PyFunction::class.java)
    assertEquals("object", (resolved as PyFunction).containingClass?.name)
  }

  private fun addClientModule() {
    myFixture.addFileToProject("bla/__init__.py", "")
    myFixture.addFileToProject("bla/_client.py", """
      class Client:
          def __init__(self):
              self.reqs = Reqs()


      class Reqs:
          def not_exists(self): ...
          def other(self): ...
    """.trimIndent())
  }

  // --- Helpers ---

  /** Finds the first string literal argument in the first call expression inside [func]. */
  private fun findFirstStringArg(func: PyFunction): PyStringLiteralExpression? {
    val callExpr = findCallInFunction(func) ?: return null
    return callExpr.argumentList?.arguments?.firstOrNull() as? PyStringLiteralExpression
  }

  /** Finds the second positional argument (string) in the first call expression inside [func]. */
  private fun findSecondStringArg(func: PyFunction): PyStringLiteralExpression? {
    val callExpr = findCallInFunction(func) ?: return null
    val args = callExpr.argumentList?.arguments ?: return null
    return args.getOrNull(1) as? PyStringLiteralExpression
  }

  /** Finds the first `monkeypatch.setattr(...)` or `monkeypatch.delattr(...)` call in [func]. */
  private fun findCallInFunction(func: PyFunction): PyCallExpression? {
    val stmts = func.statementList.statements
    for (stmt in stmts) {
      val expr = (stmt as? PyExpressionStatement)?.expression
      if (expr is PyCallExpression) return expr
    }
    return null
  }
}
