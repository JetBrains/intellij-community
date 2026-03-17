// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing

import com.intellij.idea.TestFor
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.PyWithStatement
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.testing.pyMock.PyMockPatchTargetReferenceSet
import com.jetbrains.python.testing.pyMock.getPatchCall
import com.jetbrains.python.testing.pyMock.getPatchObjectCall

/**
 * Tests for `unittest.mock.patch` IDE support (PY-78622):
 * navigation, type inference, and reference softness.
 */
@TestFor(issues = ["PY-78622"])
class PyMockTest : PyTestCase() {

  companion object {
    const val TEST_DATA_SUBDIR = "/testing/pyMock"
  }

  override fun getTestDataPath(): String = super.getTestDataPath() + TEST_DATA_SUBDIR

  override fun setUp() {
    super.setUp()
    myFixture.copyDirectoryToProject("", "")
  }

  // --- Navigation ---

  fun testNavigationToClass() {
    myFixture.configureByFile("test_patch_references/test.py")
    val file = myFixture.file as PyFile
    val testClass = file.findTopLevelClass("TestPatchReferences")!!
    val method = testClass.findMethodByName("test_patch_class", false, null)!!
    val strArg = method.decoratorList!!.decorators.first().argumentList!!.arguments.first() as PyStringLiteralExpression

    // "example_module.MyClass" has 2 segments; the last one should resolve to MyClass
    val refs = PyMockPatchTargetReferenceSet(strArg, false).createReferences()
    assertEquals(2, refs.size)
    val resolved = refs[1].resolve()
    assertNotNull("MyClass segment should resolve", resolved)
    assertInstanceOf(resolved, PyClass::class.java)
    assertEquals("MyClass", (resolved as PyClass).name)
  }

  fun testNavigationToMethod() {
    myFixture.configureByFile("test_patch_references/test.py")
    val file = myFixture.file as PyFile
    val testClass = file.findTopLevelClass("TestPatchReferences")!!
    val method = testClass.findMethodByName("test_patch_method", false, null)!!
    val strArg = method.decoratorList!!.decorators.first().argumentList!!.arguments.first() as PyStringLiteralExpression

    // "example_module.MyClass.my_method" has 3 segments; the last one should resolve to my_method
    val refs = PyMockPatchTargetReferenceSet(strArg, false).createReferences()
    assertEquals(3, refs.size)
    val resolved = refs[2].resolve()
    assertNotNull("my_method segment should resolve", resolved)
    assertInstanceOf(resolved, PyFunction::class.java)
    assertEquals("my_method", (resolved as PyFunction).name)
  }

  // --- Reference Softness ---

  fun testUnresolvedTargetIsHardByDefault() {
    myFixture.configureByFile("test_patch_references/test.py")
    val file = myFixture.file as PyFile
    val testClass = file.findTopLevelClass("TestPatchReferences")!!
    val method = testClass.findMethodByName("test_unresolved", false, null)!!

    val decorator = method.decoratorList!!.decorators.first()
    val strArg = decorator.argumentList!!.arguments.first() as PyStringLiteralExpression
    val refs = PyMockPatchTargetReferenceSet(strArg, false).createReferences()
    assertTrue("Patch target should have references", refs.isNotEmpty())

    // The last segment 'DoesNotExist' should not be soft and should not resolve
    val lastRef = refs.last()
    assertFalse("Reference should not be soft without create=True", lastRef.isSoft)
    assertNull("Unresolved segment should not resolve", lastRef.resolve())
  }

  fun testCreateTrueMakesReferencesSoft() {
    myFixture.configureByFile("test_patch_references/test.py")
    val file = myFixture.file as PyFile
    val testClass = file.findTopLevelClass("TestPatchReferences")!!
    val method = testClass.findMethodByName("test_create_true", false, null)!!

    val decorator = method.decoratorList!!.decorators.first()
    val strArg = decorator.argumentList!!.arguments.first() as PyStringLiteralExpression
    val refs = PyMockPatchTargetReferenceSet(strArg, true).createReferences()

    val lastRef = refs.last()
    assertTrue("Reference should be soft when create=True", lastRef.isSoft)
  }

  // --- Type Inference ---

  fun testMockTypeInference() {
    val file = myFixture.configureByFile("test_patch_types/test.py") as PyFile
    val func = file.findTopLevelClass("TestPatchTypes")!!
      .findMethodByName("test_default_type", false, null)!!
    val param = func.parameterList.findParameterByName("mock_method")
    assertNotNull("Parameter mock_method not found", param)

    val context = TypeEvalContext.codeAnalysis(myFixture.project, file)
    val type = context.getType(param!!)
    assertNotNull("Type should be inferred for mock parameter", type)
    assertTrue(
      "Expected MagicMock type, got: $type",
      type.toString().contains("MagicMock"),
    )
  }

  fun testNewCallableTypeInference() {
    val file = myFixture.configureByFile("test_patch_types/test.py") as PyFile
    val func = file.findTopLevelClass("TestPatchTypes")!!
      .findMethodByName("test_async_mock_type", false, null)!!
    val param = func.parameterList.findParameterByName("mock_method")
    assertNotNull("Parameter mock_method not found", param)

    val context = TypeEvalContext.codeAnalysis(myFixture.project, file)
    val type = context.getType(param!!)
    assertNotNull("Type should be inferred for new_callable parameter", type)
    assertTrue(
      "Expected AsyncMock type, got: $type",
      type.toString().contains("AsyncMock"),
    )
  }

  fun testNoInjectedParamWhenNewProvided() {
    val file = myFixture.configureByFile("test_patch_types/test.py") as PyFile
    val func = file.findTopLevelClass("TestPatchTypes")!!
      .findMethodByName("test_no_injection_when_new", false, null)!!
    // Function has only 'self', no injected param
    val paramNames = func.parameterList.parameters.map { it.name }
    assertTrue(
      "Function with new= should not receive injected parameter: $paramNames",
      "self" in paramNames && paramNames.size == 1,
    )
  }

  fun testNoInjectedParamWhenPositionalNewProvided() {
    val file = myFixture.configureByFile("test_patch_types/test.py") as PyFile
    val func = file.findTopLevelClass("TestPatchTypes")!!
      .findMethodByName("test_no_injection_when_positional_new", false, null)!!
    // Function has only 'self', no injected param — new is passed positionally
    val paramNames = func.parameterList.parameters.map { it.name }
    assertTrue(
      "Function with positional new should not receive injected parameter: $paramNames",
      "self" in paramNames && paramNames.size == 1,
    )
  }

  fun testStackedPatchOrdering() {
    val file = myFixture.configureByFile("test_patch_types/test.py") as PyFile
    val func = file.findTopLevelClass("TestPatchTypes")!!
      .findMethodByName("test_stacked_patch", false, null)!!

    val context = TypeEvalContext.codeAnalysis(myFixture.project, file)

    val mockMethod = func.parameterList.findParameterByName("mock_method")
    val mockFunction = func.parameterList.findParameterByName("mock_function")
    assertNotNull(mockMethod)
    assertNotNull(mockFunction)

    val typeMockMethod = context.getType(mockMethod!!)
    val typeMockFunction = context.getType(mockFunction!!)
    assertNotNull("mock_method should have a type", typeMockMethod)
    assertNotNull("mock_function should have a type", typeMockFunction)
    assertTrue("mock_method should be MagicMock", typeMockMethod.toString().contains("MagicMock"))
    assertTrue("mock_function should be MagicMock", typeMockFunction.toString().contains("MagicMock"))
  }

  // --- with patch(...) context manager ---

  fun testNavigationWithPatchContextManager() {
    myFixture.configureByFile("test_patch_references/test.py")
    val file = myFixture.file as PyFile
    val testClass = file.findTopLevelClass("TestPatchReferences")!!
    val method = testClass.findMethodByName("test_with_patch_class", false, null)!!

    val strArg = withPatchStringArg(method)
    assertNotNull("Expected string literal in with patch()", strArg)

    val callExpr = getPatchCall(strArg!!)
    assertNotNull("getPatchCall should recognise with patch() usage", callExpr)

    // "example_module.MyClass" has 2 segments; the last one should resolve to MyClass
    val refs = PyMockPatchTargetReferenceSet(strArg, false).createReferences()
    assertEquals(2, refs.size)
    val resolved = refs[1].resolve()
    assertNotNull("MyClass segment should resolve inside with patch()", resolved)
    assertInstanceOf(resolved, PyClass::class.java)
    assertEquals("MyClass", (resolved as PyClass).name)
  }

  fun testNavigationToMethodWithPatchContextManager() {
    myFixture.configureByFile("test_patch_references/test.py")
    val file = myFixture.file as PyFile
    val testClass = file.findTopLevelClass("TestPatchReferences")!!
    val method = testClass.findMethodByName("test_with_patch_method", false, null)!!

    val strArg = withPatchStringArg(method)
    assertNotNull("Expected string literal in with patch()", strArg)

    // "example_module.MyClass.my_method" has 3 segments; the last should resolve to my_method
    val refs = PyMockPatchTargetReferenceSet(strArg!!, false).createReferences()
    assertEquals(3, refs.size)
    val resolved = refs[2].resolve()
    assertNotNull("my_method segment should resolve inside with patch()", resolved)
    assertInstanceOf(resolved, PyFunction::class.java)
    assertEquals("my_method", (resolved as PyFunction).name)
  }

  /** Extracts the first positional string from the first `with patch(...)` in [method]. */
  private fun withPatchStringArg(method: PyFunction): PyStringLiteralExpression? {
    val withStmt = method.statementList.statements.filterIsInstance<PyWithStatement>().firstOrNull() ?: return null
    val callExpr = withStmt.withItems.firstOrNull()?.expression as? PyCallExpression ?: return null
    return callExpr.argumentList?.arguments?.firstOrNull() as? PyStringLiteralExpression
  }

  // --- target= keyword argument ---

  fun testNavigationWithTargetKeyword() {
    myFixture.configureByFile("test_patch_references/test.py")
    val file = myFixture.file as PyFile
    val testClass = file.findTopLevelClass("TestPatchReferences")!!
    val method = testClass.findMethodByName("test_patch_class_with_target_kw", false, null)!!

    // The first argument is a PyKeywordArgument; its value is the string literal
    val kwArg = method.decoratorList!!.decorators.first().argumentList!!.arguments.first() as? PyKeywordArgument
    assertNotNull("Expected keyword argument", kwArg)
    val strArg = kwArg!!.valueExpression as? PyStringLiteralExpression
    assertNotNull("Expected string literal as target= value", strArg)

    // getPatchCall must recognize target= keyword
    val callExpr = getPatchCall(strArg!!)
    assertNotNull("getPatchCall should recognize target= keyword argument", callExpr)

    // "example_module.MyClass" has 2 segments; the last one should resolve to MyClass
    val refs = PyMockPatchTargetReferenceSet(strArg, false).createReferences()
    assertEquals(2, refs.size)
    val resolved = refs[1].resolve()
    assertNotNull("MyClass segment should resolve via target= keyword", resolved)
    assertInstanceOf(resolved, PyClass::class.java)
    assertEquals("MyClass", (resolved as PyClass).name)
  }

  // --- patch.object() ---

  fun testPatchObjectNavigationToMethod() {
    myFixture.configureByFile("test_patch_object/test.py")
    val file = myFixture.file as PyFile
    val testClass = file.findTopLevelClass("TestPatchObject")!!
    val method = testClass.findMethodByName("test_patch_object_method", false, null)!!
    val decorator = method.decoratorList!!.decorators.first()
    val args = decorator.argumentList!!.arguments
    val strArg = args[1] as PyStringLiteralExpression

    val callExpr = getPatchObjectCall(strArg)
    assertNotNull("getPatchObjectCall should recognize @patch.object string", callExpr)

    val refs = strArg.references
    assertTrue("Attribute reference should exist", refs.isNotEmpty())
    val resolved = refs.last().resolve()
    assertNotNull("my_method should resolve", resolved)
    assertInstanceOf(resolved, PyFunction::class.java)
    assertEquals("my_method", (resolved as PyFunction).name)
  }

  fun testPatchObjectNavigationToAttr() {
    myFixture.configureByFile("test_patch_object/test.py")
    val file = myFixture.file as PyFile
    val testClass = file.findTopLevelClass("TestPatchObject")!!
    val method = testClass.findMethodByName("test_patch_object_attr", false, null)!!
    val decorator = method.decoratorList!!.decorators.first()
    val args = decorator.argumentList!!.arguments
    val strArg = args[1] as PyStringLiteralExpression

    val refs = strArg.references
    assertTrue("Attribute reference should exist", refs.isNotEmpty())
    val resolved = refs.last().resolve()
    assertNotNull("class_attr should resolve", resolved)
  }

  fun testPatchObjectUnresolvedIsHard() {
    myFixture.configureByFile("test_patch_object/test.py")
    val file = myFixture.file as PyFile
    val testClass = file.findTopLevelClass("TestPatchObject")!!
    val method = testClass.findMethodByName("test_patch_object_unresolved", false, null)!!
    val decorator = method.decoratorList!!.decorators.first()
    val args = decorator.argumentList!!.arguments
    val strArg = args[1] as PyStringLiteralExpression

    val refs = strArg.references
    assertTrue("Reference should exist", refs.isNotEmpty())
    val lastRef = refs.last()
    assertFalse("Reference should not be soft without create=True", lastRef.isSoft)
    assertNull("Unresolved attribute should not resolve", lastRef.resolve())
  }

  fun testPatchObjectCreateTrueMakesSoft() {
    myFixture.configureByFile("test_patch_object/test.py")
    val file = myFixture.file as PyFile
    val testClass = file.findTopLevelClass("TestPatchObject")!!
    val method = testClass.findMethodByName("test_patch_object_create_true", false, null)!!
    val decorator = method.decoratorList!!.decorators.first()
    val args = decorator.argumentList!!.arguments
    val strArg = args[1] as PyStringLiteralExpression

    val refs = strArg.references
    assertTrue("Reference should exist", refs.isNotEmpty())
    assertTrue("Reference should be soft with create=True", refs.last().isSoft)
  }

  fun testPatchObjectTypeInference() {
    val file = myFixture.configureByFile("test_patch_object/test.py") as PyFile
    val func = file.findTopLevelClass("TestPatchObject")!!
      .findMethodByName("test_patch_object_method", false, null)!!
    val param = func.parameterList.findParameterByName("mock_method")
    assertNotNull("Parameter mock_method not found", param)

    val context = TypeEvalContext.codeAnalysis(myFixture.project, file)
    val type = context.getType(param!!)
    assertNotNull("Type should be inferred for patch.object parameter", type)
    assertTrue(
      "Expected MagicMock type, got: $type",
      type.toString().contains("MagicMock"),
    )
  }

  fun testPatchObjectModuleTarget() {
    myFixture.configureByFile("test_patch_object/test.py")
    val file = myFixture.file as PyFile
    val testClass = file.findTopLevelClass("TestPatchObject")!!
    val method = testClass.findMethodByName("test_patch_object_module_target", false, null)!!
    val decorator = method.decoratorList!!.decorators.first()
    val args = decorator.argumentList!!.arguments
    val strArg = args[1] as PyStringLiteralExpression

    val refs = strArg.references
    assertTrue("Attribute reference should exist for module target", refs.isNotEmpty())
    val resolved = refs.last().resolve()
    assertNotNull("top_level_function should resolve on module target", resolved)
    assertInstanceOf(resolved, PyFunction::class.java)
    assertEquals("top_level_function", (resolved as PyFunction).name)
  }

  // --- Argument Count Inspection ---

  fun testPatchArgumentCountInspection() {
    myFixture.configureByFile("test_patch_arg_count/test.py")
    myFixture.enableInspections(com.jetbrains.python.testing.pyMock.PyMockPatchArgumentCountInspection::class.java)
    myFixture.checkHighlighting(true, false, true)
  }

  // --- patch.dict() ---

  fun testPatchDictPositionalTarget() {
    myFixture.configureByFile("test_patch_dict/test.py")
    val file = myFixture.file as PyFile
    val testClass = file.findTopLevelClass("TestPatchDict")!!
    val method = testClass.findMethodByName("test_patch_dict_positional", false, null)!!
    val decorator = method.decoratorList!!.decorators.first()
    val strArg = decorator.argumentList!!.arguments.first() as PyStringLiteralExpression

    val callExpr = getPatchCall(strArg)
    assertNotNull("getPatchCall should recognize @patch.dict string target", callExpr)

    val refs = PyMockPatchTargetReferenceSet(strArg, false).createReferences()
    assertEquals("example_module.TOP_LEVEL_VAR should produce 2 references", 2, refs.size)
  }

  fun testPatchDictKeywordTarget() {
    myFixture.configureByFile("test_patch_dict/test.py")
    val file = myFixture.file as PyFile
    val testClass = file.findTopLevelClass("TestPatchDict")!!
    val method = testClass.findMethodByName("test_patch_dict_keyword", false, null)!!
    val decorator = method.decoratorList!!.decorators.first()
    val kwArg = decorator.argumentList!!.arguments.first() as? PyKeywordArgument
    assertNotNull("Expected keyword argument", kwArg)
    val strArg = kwArg!!.valueExpression as? PyStringLiteralExpression
    assertNotNull("Expected string literal as in_dict= value", strArg)

    val callExpr = getPatchCall(strArg!!)
    assertNotNull("getPatchCall should recognize in_dict= keyword argument", callExpr)
  }

  fun testWithPatchDict() {
    myFixture.configureByFile("test_patch_dict/test.py")
    val file = myFixture.file as PyFile
    val testClass = file.findTopLevelClass("TestPatchDict")!!
    val method = testClass.findMethodByName("test_with_patch_dict", false, null)!!

    val strArg = withPatchStringArg(method)
    assertNotNull("Expected string literal in with patch.dict()", strArg)

    val callExpr = getPatchCall(strArg!!)
    assertNotNull("getPatchCall should recognize with patch.dict() usage", callExpr)
  }

  // --- patch.multiple() ---

  fun testPatchMultiplePositionalTarget() {
    myFixture.configureByFile("test_patch_multiple/test.py")
    val file = myFixture.file as PyFile
    val testClass = file.findTopLevelClass("TestPatchMultiple")!!
    val method = testClass.findMethodByName("test_patch_multiple_positional", false, null)!!
    val decorator = method.decoratorList!!.decorators.first()
    val strArg = decorator.argumentList!!.arguments.first() as PyStringLiteralExpression

    val callExpr = getPatchCall(strArg)
    assertNotNull("getPatchCall should recognize @patch.multiple string target", callExpr)

    val refs = PyMockPatchTargetReferenceSet(strArg, false).createReferences()
    assertEquals("example_module should produce 1 reference", 1, refs.size)
  }

  fun testPatchMultipleKeywordTarget() {
    myFixture.configureByFile("test_patch_multiple/test.py")
    val file = myFixture.file as PyFile
    val testClass = file.findTopLevelClass("TestPatchMultiple")!!
    val method = testClass.findMethodByName("test_patch_multiple_keyword", false, null)!!
    val decorator = method.decoratorList!!.decorators.first()
    val kwArg = decorator.argumentList!!.arguments.first() as? PyKeywordArgument
    assertNotNull("Expected keyword argument", kwArg)
    val strArg = kwArg!!.valueExpression as? PyStringLiteralExpression
    assertNotNull("Expected string literal as target= value", strArg)

    val callExpr = getPatchCall(strArg!!)
    assertNotNull("getPatchCall should recognize target= keyword argument for patch.multiple", callExpr)
  }

  fun testWithPatchMultiple() {
    myFixture.configureByFile("test_patch_multiple/test.py")
    val file = myFixture.file as PyFile
    val testClass = file.findTopLevelClass("TestPatchMultiple")!!
    val method = testClass.findMethodByName("test_with_patch_multiple", false, null)!!

    val strArg = withPatchStringArg(method)
    assertNotNull("Expected string literal in with patch.multiple()", strArg)

    val callExpr = getPatchCall(strArg!!)
    assertNotNull("getPatchCall should recognize with patch.multiple() usage", callExpr)
  }

  // --- Reference Count ---

  fun testReferenceSetCreatesCorrectCount() {
    myFixture.configureByFile("test_patch_references/test.py")
    val file = myFixture.file as PyFile
    val testClass = file.findTopLevelClass("TestPatchReferences")!!

    // "example_module.MyClass" → 2 references
    val patchClassMethod = testClass.findMethodByName("test_patch_class", false, null)!!
    val decorator = patchClassMethod.decoratorList!!.decorators.first()
    val strArg = decorator.argumentList!!.arguments.first() as? PyStringLiteralExpression
    assertNotNull("First arg should be a string literal", strArg)

    // Verify getPatchCall recognizes it
    val callExpr = getPatchCall(strArg!!)
    assertNotNull("getPatchCall should recognize @patch string", callExpr)

    // Direct reference set test
    val directRefs = PyMockPatchTargetReferenceSet(strArg, false).createReferences()
    assertEquals("example_module.MyClass should produce 2 references", 2, directRefs.size)

    // "example_module.MyClass.my_method" → 3 references
    val patchMethodMethod = testClass.findMethodByName("test_patch_method", false, null)!!
    val decoratorForMethod = patchMethodMethod.decoratorList!!.decorators.first()
    val strArgForMethod = decoratorForMethod.argumentList!!.arguments.first() as? PyStringLiteralExpression
    assertNotNull("First arg should be a string literal", strArgForMethod)
    val directRefs2 = PyMockPatchTargetReferenceSet(strArgForMethod!!, false).createReferences()
    assertEquals("example_module.MyClass.my_method should produce 3 references", 3, directRefs2.size)
  }
}
