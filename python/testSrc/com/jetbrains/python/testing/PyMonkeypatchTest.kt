// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing

import com.intellij.idea.TestFor
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.testing.pyMock.PyMockPatchTargetReferenceSet

/**
 * Tests for `monkeypatch.setattr` / `monkeypatch.delattr` IDE support:
 * navigation, completion, and reference resolution.
 */
@TestFor(issues = ["PY-78622"])
class PyMonkeypatchTest : PyTestCase() {

  companion object {
    const val TEST_DATA_SUBDIR = "/testing/pyMonkeypatch"
  }

  override fun getTestDataPath(): String = super.getTestDataPath() + TEST_DATA_SUBDIR

  override fun setUp() {
    super.setUp()
    myFixture.copyDirectoryToProject("", "")
  }

  // --- Dotted string form: monkeypatch.setattr("module.Class.attr", value) ---

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
    assertEquals(3, refs.size)
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
    assertEquals(2, refs.size)
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
    assertEquals(3, refs.size)
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
      val expr = (stmt as? com.jetbrains.python.psi.PyExpressionStatement)?.expression
      if (expr is PyCallExpression) return expr
    }
    return null
  }
}
