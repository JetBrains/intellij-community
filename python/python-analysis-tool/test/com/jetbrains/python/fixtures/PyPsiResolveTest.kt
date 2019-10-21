package com.jetbrains.python.fixtures

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.PsiReference
import com.intellij.psi.ResolveResult
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.resolve.ImportedResolveResult
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.pyi.PyiUtil

class PyPsiResolveTest : PyPsiResolveTestCase() {
  override fun doResolve(): PsiElement? {
    val ref = findReferenceByMarker()
    return ref?.resolve()
  }

  private fun resolve(): PsiElement? {
    val ref = configureByFileAndFindReference("resolve/" + getTestName(false) + ".py")
    return ref?.resolve()
  }

  private fun findReferenceByMarker(): PsiReference? {
    configureByFile("resolve/" + getTestName(false) + ".py")
    return myPsiFile?.let { findReferenceByMarker(it) }
  }

  private fun multiResolve(): Array<ResolveResult> {
    val ref = findReferenceByMarker()
    assertTrue(ref is PsiPolyVariantReference)
    return (ref as PsiPolyVariantReference).multiResolve(false)
  }

  fun testFunc() {
    val targetElement = resolve()
    assertTrue(targetElement is PyFunction)
  }

  fun testToConstructor() {
    val target = resolve()
    assertTrue(target is PyFunction)
    assertEquals(PyNames.INIT, (target as PyFunction).name)
  }

  fun testInitOrNewReturnsInitWhenNewIsFirst() {
    doTestInitOrNewReturnsInit()
  }

  fun testInitOrNewReturnsInitWhenNewIsLast() {
    doTestInitOrNewReturnsInit()
  }

  private fun doTestInitOrNewReturnsInit() {
    configureByFile("resolve/" + getTestName(false) + ".py")
    val pyClass = PsiTreeUtil.findChildOfType(myPsiFile, PyClass::class.java)
    assertNotNull(pyClass)
    val init = pyClass!!.findInitOrNew(false, TypeEvalContext.userInitiated(myProject, myPsiFile))
    assertEquals(PyNames.INIT, init!!.name)
  }

  fun testToConstructorInherited() {
    val targets = multiResolve()
    assertEquals(1, targets.size) // to class, to init
    val elt: PsiElement? = targets[0].element
    assertTrue(elt is PyClass)
    assertEquals("Bar", (elt as PyClass).name)
  }

  fun testComplexCallee() {
    val targetElement = resolve()
    val assigned = (targetElement?.context as PyAssignmentStatement).assignedValue
    assertTrue(assigned is PyCallExpression)
    val callee = (assigned as PyCallExpression).callee
    assertTrue(callee is PySubscriptionExpression)
  }

  fun testVar() {
    val targetElement = resolve()
    assertTrue(targetElement is PyTargetExpression)
  }

  fun testDefaultInClass() {
    val targetElement = resolve()
    assertNotNull(targetElement)
    assertTrue(targetElement is PyTargetExpression)
    assertEquals("FOO", (targetElement as PyTargetExpression).name)
  }

  fun testQualifiedFunc() {
    val targetElement = resolve()
    assertTrue(targetElement is PyFunction)
  }

  fun testQualifiedVar() {
    val targetElement = resolve()
    assertTrue(targetElement is PyTargetExpression)
  }

  fun testQualifiedTarget() {
    val targetElement = resolve()
    assertTrue(targetElement is PyTargetExpression)
  }

  fun testQualifiedFalseTarget() {
    val targetElement = resolve()
    assertNull(targetElement)
  }

  fun testInnerFuncVar() {
    val targetElement = resolve()
    assertTrue(targetElement is PyTargetExpression)
  }

  fun testTupleInComprh() {
    val targetElement = resolve()
    assertTrue(targetElement is PyTargetExpression)
  }

  fun testForStatement() {
    val targetElement = resolve()
    assertTrue(targetElement is PyTargetExpression)
  }

  fun testExceptClause() {
    val targetElement = resolve()
    assertTrue(targetElement is PyTargetExpression)
  }

  fun testLookAhead() {
    val targetElement = resolve()
    assertTrue(targetElement is PyTargetExpression)
  }

  fun testLookAheadCapped() {
    val targetElement = resolve()
    assertTrue(targetElement is PyTargetExpression)
  }

  fun testTryExceptElse() {
    val targetElement = resolve()
    assertTrue(targetElement is PyTargetExpression)
  }

  fun testGlobal() {
    val targetElement = resolve()
    assertTrue(targetElement is PyTargetExpression)
    assertTrue(targetElement?.parent is PyAssignmentStatement)
  }

  fun testGlobalInNestedFunction() {
    val targetElement = resolve()
    assertInstanceOf(targetElement, PyTargetExpression::class.java)
    assertInstanceOf(ScopeUtil.getScopeOwner(targetElement), PyFile::class.java)
  }

  fun testGlobalDefinedLocally() {
    val element = resolve()
    assertInstanceOf(element, PyTargetExpression::class.java)
    val parent = element?.parent
    assertInstanceOf(parent, PyAssignmentStatement::class.java)
  }

  fun testLambda() {
    val targetElement = resolve()
    assertTrue(targetElement is PyNamedParameter)
  }

  fun testLambdaParameterOutside() {
    val targetElement = resolve()
    assertTrue(targetElement is PyTargetExpression)
  }

  fun testSuperField() {
    val targetElement = resolve()
    assertTrue(targetElement is PyTargetExpression)
  }

  fun testFieldInCondition() {
    val targetElement = resolve()
    assertTrue(targetElement is PyTargetExpression)
  }

  fun testMultipleFields() {
    val targetElement = resolve()
    assertTrue(targetElement is PyTargetExpression)
  }

  fun testClassPeerMembers() {
    val target = resolve()
    assertTrue(target is PyFunction)
  }

  fun testTuple() {
    val targetElement = resolve()
    assertTrue(targetElement is PyTargetExpression)
    assertTrue(targetElement?.parent is PyAssignmentStatement)
  }

  fun testMultiTarget() {
    val targetElement = resolve()
    assertTrue(targetElement is PyTargetExpression)
    assertTrue(targetElement?.parent is PyAssignmentStatement)
  }

  fun testMultiTargetTuple() {
    val targetElement = resolve()
    assertTrue(targetElement is PyTargetExpression)
    assertNotNull(PsiTreeUtil.getParentOfType(targetElement, PyAssignmentStatement::class.java)) // it's deep in a tuple
  }

  fun testWithStatement() {
    val targetElement = resolve()
    assertTrue(targetElement is PyTargetExpression)
    assertTrue(targetElement?.parent is PyWithItem)
  }

  fun testTupleInExcept() {
    val targetElement = resolve()
    assertTrue(targetElement is PyTargetExpression)
    assertNotNull(PsiTreeUtil.getParentOfType(targetElement, PyExceptPart::class.java))
  }


  fun testDocStringClass() {
    val targetElement = resolve()
    assertTrue(targetElement is PyStringLiteralExpression)
    assertEquals("Docstring of class Foo", (targetElement as PyStringLiteralExpression).stringValue)
  }

  fun testDocStringInstance() {
    val targetElement = resolve()
    assertTrue(targetElement is PyStringLiteralExpression)
    assertEquals("Docstring of class Foo", (targetElement as PyStringLiteralExpression).stringValue)
  }

  fun testDocStringFunction() {
    val targetElement = resolve()
    assertTrue(targetElement is PyStringLiteralExpression)
    assertEquals("Docstring of function bar", (targetElement as PyStringLiteralExpression).stringValue)
  }

  fun testDocStringInvalid() {
    val targetElement = resolve()
    assertNull(targetElement)
  }

  fun testFieldNotInInit() {
    val targetElement = resolve()
    assertTrue(targetElement is PyTargetExpression)
  }

  fun testClassIsNotMemberOfItself() {
    val targetElement = resolve()
    assertNull(targetElement)
  }

  fun testSuper() {
    val targetElement = resolve()
    assertTrue(targetElement is PyFunction)
    assertEquals("A", (targetElement as PyFunction).containingClass!!.name)
  }

  fun testSuperPy3k() {  // PY-1330
    runWithLanguageLevel(
      LanguageLevel.PYTHON34
    ) {
      val pyFunction = assertResolvesTo(PyFunction::class.java, "foo")
      assertEquals("A", pyFunction.containingClass!!.name)
    }
  }

  fun testStackOverflow() {
    val targetElement = resolve()
    assertNull(targetElement)
  }

  fun testProperty() {
    val targetElement = resolve()
    assertTrue(targetElement is PyFunction)
    assertEquals("set_full_name", (targetElement as PyFunction).name)
  }

  fun testLambdaWithParens() {  // PY-882
    val targetElement = resolve()
    assertTrue(targetElement is PyParameter)
  }

  fun testTextBasedResolve() {
    val resolveResults = multiResolve()
    assertEquals(1, resolveResults.size)
    assertTrue(resolveResults[0].element is PyFunction)
  }

  fun testClassPrivateInClass() {
    val targetElement = resolve()
    assertTrue(targetElement is PyTargetExpression)
    assertTrue(targetElement?.parent is PyAssignmentStatement)
  }

  fun testClassPrivateInMethod() {
    val targetElement = resolve()
    assertTrue(targetElement is PyTargetExpression)
    assertTrue(targetElement?.parent is PyAssignmentStatement)
  }

  fun testClassPrivateInMethodNested() {
    val targetElement = resolve()
    assertTrue(targetElement is PyTargetExpression)
    assertTrue(targetElement?.parent is PyAssignmentStatement)
  }

  fun testClassPrivateInherited() {
    val targetElement = resolve()
    assertTrue(targetElement is PyTargetExpression)
    assertTrue(targetElement?.parent is PyAssignmentStatement)
  }

  fun testClassPrivateOutsideClass() {
    val targetElement = resolve()
    assertNull(targetElement)
  }

  fun testClassPrivateOutsideInstance() {
    val targetElement = resolve()
    assertNull(targetElement)
  }

  fun testClassNameEqualsMethodName() {
    val targetElement = resolve()
    assertInstanceOf(targetElement, PyFunction::class.java)
  }

  fun testUnresolvedImport() {
    val results = multiResolve()
    assertEquals(1, results.size)
    assertInstanceOf(results[0], ImportedResolveResult::class.java)
    val result = results[0] as ImportedResolveResult
    assertNull(result.element)
  }

  fun testIsInstance() {  // PY-1133
    val targetElement = resolve()
    assertInstanceOf(targetElement, PyNamedParameter::class.java)
  }

  fun testListComprehension() { // PY-1143
    val targetElement = resolve()
    assertInstanceOf(targetElement, PyTargetExpression::class.java)
  }

  fun testSuperMetaClass() {
    assertResolvesTo(PyFunction::class.java, "foo")
  }

  //PY-28562
  fun testSuperMetaClassInheritsObject() {
    assertResolvesTo(PyFunction::class.java, "__getattribute__")
  }


  fun testSuperDunderClass() {  // PY-1190
    assertResolvesTo(PyFunction::class.java, "foo")
  }

  fun testSuperTwoClasses() {  // PY-2133
    val pyFunction = assertResolvesTo(PyFunction::class.java, "my_call")
    assertEquals("Base2", pyFunction.containingClass!!.name)
  }

  fun testLambdaDefaultParameter() {
    val element = doResolve()
    assertInstanceOf(element, PyTargetExpression::class.java)
    assertTrue(element?.parent is PySetCompExpression)
  }

  fun testListAssignment() {
    val element = doResolve()
    assertInstanceOf(element, PyTargetExpression::class.java)
  }

  fun testStarUnpacking() {  // PY-1459
    assertResolvesTo(LanguageLevel.PYTHON34, PyTargetExpression::class.java, "heads")
  }

  fun testStarUnpackingInLoop() {  // PY-1525
    assertResolvesTo(LanguageLevel.PYTHON34, PyTargetExpression::class.java, "bbb")
  }

  fun testBuiltinVsClassMember() {  // PY-1654
    val pyFunction = assertResolvesTo(PyFunction::class.java, "eval")
    assertIsBuiltin(pyFunction)
  }

  fun testLambdaToClass() {  // PY-2182
    assertResolvesTo(PyClass::class.java, "TestTwo")
  }

  fun testImportInTryExcept() {  // PY-2197
    assertResolvesTo(PyFile::class.java, "sys.pyi")
  }

  fun testModuleToBuiltins() {
    val element = doResolve()
    assertNull(element)
  }

  fun testWithParentheses() {
    assertResolvesTo(LanguageLevel.PYTHON27, PyTargetExpression::class.java, "MockClass1")
  }

  fun testPrivateInsideModule() {  // PY-2618
    assertResolvesTo(PyClass::class.java, "__VeryPrivate")
  }

  fun testRedeclaredInstanceVar() {  // PY-2740
    assertResolvesTo(PyFunction::class.java, "jobsDoneCount")
  }

  fun testNoResolveIntoGenerator() {  // PY-3030
    val expr = assertResolvesTo(PyTargetExpression::class.java, "foo")
    assertEquals("foo = 1", expr.parent.text)
  }

  fun testResolveInGenerator() {
    assertResolvesTo(PyTargetExpression::class.java, "foo")
  }

  fun testNestedListComp() {  // PY-3068
    assertResolvesTo(PyTargetExpression::class.java, "yy")
  }

  fun testSuperclassResolveScope() {  // PY-3554
    assertResolvesTo(PyClass::class.java, "date", "datetime.pyi")
  }

  fun testDontResolveTargetToBuiltins() {  // PY-4256
    assertResolvesTo(PyTargetExpression::class.java, "str")
  }

  fun testKeywordArgument() {
    assertResolvesTo(PyNamedParameter::class.java, "bar")
  }

  fun testImplicitResolveInstanceAttribute() {
    val resolveResults = multiResolve()
    assertEquals(1, resolveResults.size)
    val psiElement = resolveResults[0].element
    assertTrue(psiElement is PyTargetExpression && "xyzzy" == psiElement.name)
  }

  fun testAttributeAssignedNearby() {
    assertResolvesTo(PyTargetExpression::class.java, "xyzzy")
  }

  fun testPreviousTarget() {
    val resolved = resolve()
    assertInstanceOf(resolved, PyTargetExpression::class.java)
    val target = resolved as PyTargetExpression
    val value = target.findAssignedValue()
    assertInstanceOf(value, PyNumericLiteralExpression::class.java)
  }

  fun testMetaclass() {
    val function = assertResolvesTo(PyFunction::class.java, "getStore")
    assertEquals("PluginMetaclass", function.containingClass!!.name)
  }

  // PY-6083
  fun testLambdaParameterInDecorator() {
    assertResolvesTo(PyNamedParameter::class.java, "xx")
  }

  // PY-6435
  fun testLambdaParameterInDefaultValue() {
    assertResolvesTo(PyNamedParameter::class.java, "xx")
  }

  // PY-6540
  fun testClassRedefinedField() {
    assertResolvesTo(PyClass::class.java, "Foo")
  }

  fun testKWArg() {
    assertResolvesTo(PyClass::class.java, "timedelta")
  }

  fun testShadowingTargetExpression() {
    assertResolvesTo(PyTargetExpression::class.java, "lab")
  }

  fun testReferenceInDocstring() {
    assertResolvesTo(PyClass::class.java, "datetime")
  }

  // PY-9795
  fun testGoogleDocstringParamType() {
    TODO()
    //runWithDocStringFormat(DocStringFormat.GOOGLE) { assertResolvesTo(PyClass::class.java, "datetime") }
  }

  // PY-9795
  fun testGoogleDocstringReturnType() {
    TODO()
    //runWithDocStringFormat(DocStringFormat.GOOGLE) { assertResolvesTo(PyClass::class.java, "MyClass") }
  }

  // PY-16906
  fun testGoogleDocstringModuleAttribute() {
    TODO()
    //runWithDocStringFormat(DocStringFormat.GOOGLE) { assertResolvesTo(PyTargetExpression::class.java, "module_level_variable1") }
  }

  fun testEpyDocTypeReferenceForInstanceAttributeInClassLevelDocstring() {
    TODO()
    //runWithDocStringFormat(DocStringFormat.EPYTEXT) { assertResolvesTo(PyTargetExpression::class.java, "attr") }
  }

  // PY-7541
  fun testLoopToUpperReassignment() {
    val ref = findReferenceByMarker()
    val source = ref?.element
    val target = ref?.resolve()
    assertNotNull(target)
    assertNotSame(source, target)
    assertTrue(PyPsiUtils.isBefore(target!!, source!!))
  }

  // PY-7541
  fun testLoopToLowerReassignment() {
    val ref = findReferenceByMarker()
    val source = ref?.element
    val target = ref?.resolve()
    assertNotNull(target)
    assertSame(source, target)
  }

  // PY-7970
  fun testAugmentedAssignment() {
    assertResolvesTo(PyTargetExpression::class.java, "foo")
  }

  // PY-7970
  fun testAugmentedAfterAugmented() {
    val ref = findReferenceByMarker()
    val source = ref?.element
    val resolved = ref?.resolve()
    assertInstanceOf(resolved, PyReferenceExpression::class.java)
    assertNotSame(resolved, source)
    val res = resolved as PyReferenceExpression?
    assertNotNull(res)
    assertEquals("foo", res!!.name)
    assertInstanceOf(res.parent, PyAugAssignmentStatement::class.java)
  }

  fun testGeneratorShadowing() {  // PY-8725
    assertResolvesTo(PyFunction::class.java, "_")
  }

  // PY-6805
  fun testAttributeDefinedInNew() {
    val resolved = resolve()
    assertInstanceOf(resolved, PyTargetExpression::class.java)
    val target = resolved as PyTargetExpression
    assertEquals("foo", target.name)
    val owner = ScopeUtil.getScopeOwner(target)
    assertNotNull(owner)
    assertInstanceOf(owner, PyFunction::class.java)
    assertEquals("__new__", owner!!.name)
  }

  fun testPreferInitForAttributes() {  // PY-9228
    val xyzzy = assertResolvesTo(PyTargetExpression::class.java, "xyzzy")
    assertEquals("__init__", PsiTreeUtil.getParentOfType(xyzzy, PyFunction::class.java)!!.name)
  }

  // PY-11401
  fun testResolveAttributesUsingOldStyleMROWhenUnresolvedAncestorsAndC3Fails() {
    assertResolvesTo(PyFunction::class.java, "foo")
  }

  // PY-15390
  fun testMatMul() {
    assertResolvesTo(PyFunction::class.java, "__matmul__")
  }

  // PY-15390
  fun testRMatMul() {
    assertResolvesTo(PyFunction::class.java, "__rmatmul__")
  }

  //PY-2748
  fun testFormatStringKWArgs() {
    val target = resolve()
    assertTrue(target is PyNumericLiteralExpression)
    assertEquals(12, (target as PyNumericLiteralExpression).longValue as Long)
  }

  //PY-2748
  fun testFormatPositionalArgs() {
    val target = resolve()
    assertInstanceOf(target, PyReferenceExpression::class.java)
    assertEquals("string", target?.text)
  }

  //PY-2748
  fun testFormatArgsAndKWargs() {
    val target = resolve()
    assertInstanceOf(target, PyStringLiteralExpression::class.java)
  }

  //PY-2748
  fun testFormatArgsAndKWargs1() {
    val target = resolve()
    assertTrue(target is PyStringLiteralExpression)
    assertEquals("keyword", (target as PyStringLiteralExpression).stringValue)
  }

  //PY-2748
  fun testFormatStringWithPackedDictAsArgument() {
    val target = resolve()
    assertTrue(target is PyStringLiteralExpression)
    assertEquals("\"f\"", target?.text)
  }

  //PY-2748
  fun testFormatStringWithPackedListAsArgument() {
    val target = resolve()
    assertInstanceOf(target, PyNumericLiteralExpression::class.java)
    assertEquals("1", target?.text)
  }

  //PY-2748
  fun testFormatStringWithPackedTupleAsArgument() {
    val target = resolve()
    assertInstanceOf(target, PyStringLiteralExpression::class.java)
    assertEquals("\"snd\"", target?.text)
  }

  //PY-2748
  fun testFormatStringWithRefAsArgument() {
    val target = resolve()
    assertInstanceOf(target, PyStarArgument::class.java)
  }


  //PY-2748
  fun testPercentPositionalArgs() {
    val target = resolve()
    assertTrue(target is PyStringLiteralExpression)
  }

  //PY-2748
  fun testPercentKeyWordArgs() {
    val target = resolve()
    assertTrue(target is PyNumericLiteralExpression)
    assertNotNull((target as PyNumericLiteralExpression).longValue)
    assertEquals(java.lang.Long.valueOf(4181), target.longValue)
  }

  fun testPercentStringKeyWordArgWithParentheses() {
    val target = resolve()
    assertTrue(target is PyStringLiteralExpression)
    assertEquals("s", (target as PyStringLiteralExpression).stringValue)
  }

  //PY-2748
  fun testPercentStringBinaryStatementArg() {
    val target = resolve()
    assertTrue(target is PyStringLiteralExpression)
    assertEquals("1", (target as PyStringLiteralExpression).stringValue)
  }

  //PY-2748
  fun testPercentStringArgWithRedundantParentheses() {
    val target = resolve()
    assertTrue(target is PyStringLiteralExpression)
    assertEquals("1", (target as PyStringLiteralExpression).stringValue)
  }

  //PY-2748
  fun testPercentStringWithRefAsArgument() {
    val target = resolve()
    assertEquals("tuple", target?.text)
  }

  //PY-2748
  fun testPercentStringWithOneStringArgument() {
    val target = resolve()
    assertEquals("hello", (target as PyStringLiteralExpression).stringValue)
  }

  //PY-2748
  fun testFormatStringPackedDictCall() {
    val target = resolve()
    assertInstanceOf(target, PyKeywordArgument::class.java)
  }

  //PY-2748
  fun testPercentStringDictCall() {
    val target = resolve()
    assertEquals("hello", ((target as PyKeywordArgument).valueExpression as PyStringLiteralExpression).stringValue)
  }

  // PY-2748
  fun testPercentStringParenDictCall() {
    val target = resolve()
    assertEquals("hello", ((target as PyKeywordArgument).valueExpression as PyStringLiteralExpression).stringValue)
  }

  // PY-2748
  fun testPercentStringPosParenDictCall() {
    val target = resolve()
    assertInstanceOf(target, PyCallExpression::class.java)
    assertEquals("dict()", target?.text)
  }

  // PY-2748
  fun testFormatStringWithPackedAndNonPackedArgs() {
    val target = resolve()
    assertInstanceOf(target, PyNumericLiteralExpression::class.java)
    assertEquals("2", target?.text)
  }

  fun testGlobalNotDefinedAtTopLevel() {
    assertResolvesTo(PyTargetExpression::class.java, "foo")
  }

  // PY-13734
  fun testImplicitDunderClass() {
    assertUnresolved()
  }

  fun testImplicitDunderDoc() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, PyNames.DOC)
    assertIsBuiltin(expression)
  }

  fun testImplicitDunderSizeOf() {
    assertUnresolved()
  }

  fun testImplicitUndeclaredClassAttr() {
    assertUnresolved()
  }

  fun testImplicitQualifiedClassAttr() {
    val resolveResults = multiResolve()
    assertEquals(1, resolveResults.size)
    val target = assertInstanceOf(resolveResults[0].element, PyTargetExpression::class.java)
    assertEquals("CLASS_ATTR", target.name)
    assertInstanceOf(ScopeUtil.getScopeOwner(target), PyClass::class.java)
  }

  // PY-13734
  fun testImplicitDunderClassNewStyleClass() {
    assertUnresolved()
  }

  fun testImplicitDunderDocNewStyleClass() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, PyNames.DOC)
    assertIsBuiltin(expression)
  }

  fun testImplicitDunderSizeOfNewStyleClass() {
    assertUnresolved()
  }

  fun testImplicitUndeclaredClassAttrNewStyleClass() {
    assertUnresolved()
  }

  // PY-13734
  fun testImplicitDunderClassWithClassAttr() {
    assertUnresolved()
  }

  fun testImplicitDunderDocWithClassAttr() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, PyNames.DOC)
    assertIsBuiltin(expression)
  }

  fun testImplicitDunderSizeOfWithClassAttr() {
    assertUnresolved()
  }

  fun testImplicitClassAttr() {
    assertUnresolved()
  }

  // PY-13734
  fun testImplicitDunderClassWithClassAttrNewStyleClass() {
    assertUnresolved()
  }

  fun testImplicitDunderDocWithClassAttrNewStyleClass() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, PyNames.DOC)
    assertIsBuiltin(expression)
  }

  fun testImplicitDunderSizeOfWithClassAttrNewStyleClass() {
    assertUnresolved()
  }

  fun testImplicitClassAttrNewStyleClass() {
    assertUnresolved()
  }

  // PY-13734
  fun testImplicitDunderClassWithInheritedClassAttr() {
    assertUnresolved()
  }

  fun testImplicitDunderDocWithInheritedClassAttr() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, PyNames.DOC)
    assertIsBuiltin(expression)
  }

  fun testImplicitDunderSizeOfWithInheritedClassAttr() {
    assertUnresolved()
  }

  fun testImplicitInheritedClassAttr() {
    assertUnresolved()
  }

  // PY-13734
  fun testInstanceDunderClass() {
    assertIsBuiltin(assertResolvesTo(PyFunction::class.java, PyNames.__CLASS__))
  }

  fun testInstanceDunderDoc() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, PyNames.DOC)
    assertIsBuiltin(expression)
  }

  fun testInstanceDunderSizeOf() {
    assertUnresolved()
  }

  fun testInstanceUndeclaredClassAttr() {
    assertUnresolved()
  }

  // PY-13734
  fun testInstanceDunderClassNewStyleClass() {
    assertIsBuiltin(assertResolvesTo(PyFunction::class.java, PyNames.__CLASS__))
  }

  fun testInstanceDunderDocNewStyleClass() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, PyNames.DOC)
    assertIsBuiltin(expression)
  }

  fun testInstanceDunderSizeOfNewStyleClass() {
    val expression = assertResolvesTo(PyFunction::class.java, PyNames.SIZEOF)
    assertIsBuiltin(expression)
  }

  fun testInstanceUndeclaredClassAttrNewStyleClass() {
    assertUnresolved()
  }

  // PY-13734
  fun testInstanceDunderClassWithClassAttr() {
    assertIsBuiltin(assertResolvesTo(PyFunction::class.java, PyNames.__CLASS__))
  }

  fun testInstanceDunderDocWithClassAttr() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, PyNames.DOC)

    val cls = expression.containingClass
    assertNotNull(cls)
    assertEquals("A", cls!!.name)
  }

  fun testInstanceDunderSizeOfWithClassAttr() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, PyNames.SIZEOF)

    val cls = expression.containingClass
    assertNotNull(cls)
    assertEquals("A", cls!!.name)
  }

  fun testInstanceClassAttr() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, "my_attr")

    val cls = expression.containingClass
    assertNotNull(cls)
    assertEquals("A", cls!!.name)
  }

  // PY-13734
  fun testInstanceDunderClassWithClassAttrNewStyleClass() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, PyNames.__CLASS__)

    val cls = expression.containingClass
    assertNotNull(cls)
    assertEquals("A", cls!!.name)
  }

  fun testInstanceDunderDocWithClassAttrNewStyleClass() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, PyNames.DOC)

    val cls = expression.containingClass
    assertNotNull(cls)
    assertEquals("A", cls!!.name)
  }

  fun testInstanceDunderSizeOfWithClassAttrNewStyleClass() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, PyNames.SIZEOF)

    val cls = expression.containingClass
    assertNotNull(cls)
    assertEquals("A", cls!!.name)
  }

  fun testInstanceClassAttrNewStyleClass() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, "my_attr")

    val cls = expression.containingClass
    assertNotNull(cls)
    assertEquals("A", cls!!.name)
  }

  // PY-13734
  fun testInstanceDunderClassWithInheritedClassAttr() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, PyNames.__CLASS__)

    val cls = expression.containingClass
    assertNotNull(cls)
    assertEquals("A", cls!!.name)
  }

  fun testInstanceDunderDocWithInheritedClassAttr() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, PyNames.DOC)
    assertIsBuiltin(expression)
  }

  fun testInstanceDunderSizeOfWithInheritedClassAttr() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, PyNames.SIZEOF)

    val cls = expression.containingClass
    assertNotNull(cls)
    assertEquals("A", cls!!.name)
  }

  fun testInstanceInheritedClassAttr() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, "my_attr")

    val cls = expression.containingClass
    assertNotNull(cls)
    assertEquals("A", cls!!.name)
  }

  // PY-13734
  fun testLocalDunderClass() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, PyNames.__CLASS__)

    val function = PsiTreeUtil.getParentOfType(expression, PyFunction::class.java)
    assertNotNull(function)
    assertEquals("foo", function!!.name)
  }

  // PY-13734
  fun testTypeDunderClass() {
    assertUnresolved()
  }

  fun testTypeDunderDoc() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, PyNames.DOC)
    assertIsBuiltin(expression)
  }

  fun testTypeDunderSizeOf() {
    assertUnresolved()
  }

  fun testTypeUndeclaredClassAttr() {
    assertUnresolved()
  }

  // PY-13734
  fun testTypeDunderClassNewStyleClass() {
    assertIsBuiltin(assertResolvesTo(PyFunction::class.java, PyNames.__CLASS__))
  }

  fun testTypeDunderDocNewStyleClass() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, PyNames.DOC)
    assertIsBuiltin(expression)
  }

  fun testTypeDunderSizeOfNewStyleClass() {
    val expression = assertResolvesTo(PyFunction::class.java, PyNames.SIZEOF)
    assertIsBuiltin(expression)
  }

  fun testTypeUndeclaredClassAttrNewStyleClass() {
    assertUnresolved()
  }

  // PY-13734
  fun testTypeDunderClassWithClassAttr() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, PyNames.__CLASS__)

    val cls = expression.containingClass
    assertNotNull(cls)
    assertEquals("A", cls!!.name)
  }

  fun testTypeDunderDocWithClassAttr() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, PyNames.DOC)

    val cls = expression.containingClass
    assertNotNull(cls)
    assertEquals("A", cls!!.name)
  }

  fun testTypeDunderSizeOfWithClassAttr() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, PyNames.SIZEOF)

    val cls = expression.containingClass
    assertNotNull(cls)
    assertEquals("A", cls!!.name)
  }

  fun testTypeClassAttr() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, "my_attr")

    val cls = expression.containingClass
    assertNotNull(cls)
    assertEquals("A", cls!!.name)
  }

  // PY-13734
  fun testTypeDunderClassWithClassAttrNewStyleClass() {
    assertIsBuiltin(assertResolvesTo(PyFunction::class.java, PyNames.__CLASS__))
  }

  fun testTypeDunderDocWithClassAttrNewStyleClass() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, PyNames.DOC)

    val cls = expression.containingClass
    assertNotNull(cls)
    assertEquals("A", cls!!.name)
  }

  fun testTypeDunderSizeOfWithClassAttrNewStyleClass() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, PyNames.SIZEOF)

    val cls = expression.containingClass
    assertNotNull(cls)
    assertEquals("A", cls!!.name)
  }

  fun testTypeClassAttrNewStyleClass() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, "my_attr")

    val cls = expression.containingClass
    assertNotNull(cls)
    assertEquals("A", cls!!.name)
  }

  // PY-13734
  fun testTypeDunderClassWithInheritedClassAttr() {
    assertIsBuiltin(assertResolvesTo(PyFunction::class.java, PyNames.__CLASS__))
  }

  fun testTypeDunderDocWithInheritedClassAttr() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, PyNames.DOC)
    assertIsBuiltin(expression)
  }

  fun testTypeDunderSizeOfWithInheritedClassAttr() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, PyNames.SIZEOF)

    val cls = expression.containingClass
    assertNotNull(cls)
    assertEquals("A", cls!!.name)
  }

  fun testTypeInheritedClassAttr() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, "my_attr")

    val cls = expression.containingClass
    assertNotNull(cls)
    assertEquals("A", cls!!.name)
  }

  // PY-13734
  fun testDunderClassInDeclaration() {
    assertUnresolved()
  }

  fun testDunderDocInDeclaration() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, PyNames.DOC)
    assertIsBuiltin(expression)
  }

  fun testDunderSizeOfInDeclaration() {
    assertUnresolved()
  }

  fun testUndeclaredClassAttrInDeclaration() {
    assertUnresolved()
  }

  // PY-13734
  fun testDunderClassInDeclarationNewStyleClass() {
    assertUnresolved()
  }

  fun testDunderDocInDeclarationNewStyleClass() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, PyNames.DOC)
    assertIsBuiltin(expression)
  }

  fun testDunderSizeOfInDeclarationNewStyleClass() {
    assertUnresolved()
  }

  fun testUndeclaredClassAttrInDeclarationNewStyleClass() {
    assertUnresolved()
  }

  // PY-13734
  fun testDunderClassInDeclarationWithClassAttr() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, PyNames.__CLASS__)

    val cls = expression.containingClass
    assertNotNull(cls)
    assertEquals("A", cls!!.name)
  }

  fun testDunderDocInDeclarationWithClassAttr() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, PyNames.DOC)

    val cls = expression.containingClass
    assertNotNull(cls)
    assertEquals("A", cls!!.name)
  }

  fun testDunderSizeOfInDeclarationWithClassAttr() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, PyNames.SIZEOF)

    val cls = expression.containingClass
    assertNotNull(cls)
    assertEquals("A", cls!!.name)
  }

  fun testClassAttrInDeclaration() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, "my_attr")

    val cls = expression.containingClass
    assertNotNull(cls)
    assertEquals("A", cls!!.name)
  }

  // PY-13734
  fun testDunderClassInDeclarationWithClassAttrNewStyleClass() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, PyNames.__CLASS__)

    val cls = expression.containingClass
    assertNotNull(cls)
    assertEquals("A", cls!!.name)
  }

  fun testDunderDocInDeclarationWithClassAttrNewStyleClass() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, PyNames.DOC)

    val cls = expression.containingClass
    assertNotNull(cls)
    assertEquals("A", cls!!.name)
  }

  fun testDunderSizeOfInDeclarationWithClassAttrNewStyleClass() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, PyNames.SIZEOF)

    val cls = expression.containingClass
    assertNotNull(cls)
    assertEquals("A", cls!!.name)
  }

  fun testClassAttrInDeclarationNewStyleClass() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, "my_attr")

    val cls = expression.containingClass
    assertNotNull(cls)
    assertEquals("A", cls?.name)
  }

  // PY-13734
  fun testDunderClassInDeclarationWithInheritedClassAttr() {
    assertUnresolved()
  }

  fun testDunderDocInDeclarationWithInheritedClassAttr() {
    val expression = assertResolvesTo(PyTargetExpression::class.java, PyNames.DOC)
    assertIsBuiltin(expression)
  }

  fun testDunderSizeOfInDeclarationWithInheritedClassAttr() {
    assertUnresolved()
  }

  fun testInheritedClassAttrInDeclaration() {
    assertUnresolved()
  }

  // PY-13734
  fun testDunderClassInDeclarationInsideFunction() {
    assertUnresolved()
  }

  // PY-22763
  fun testComparisonOperatorReceiver() {
    val element = doResolve()
    val dunderLt = assertInstanceOf(element, PyFunction::class.java)
    assertEquals("__lt__", dunderLt.name)
    assertEquals("str", dunderLt.containingClass!!.name)
  }

  // PY-26006
  fun testSOEDecoratingFunctionWithSameNameDecorator() {
    val function = assertInstanceOf(doResolve(), PyFunction::class.java)
    assertEquals(4, function.textOffset)
  }

  // PY-23259
  fun testTypingListInheritor() {
    assertResolvesTo(PyFunction::class.java, "append")
  }

  // PY-23259
  fun testImportedTypingListInheritor() {
    TODO()
    //myFixture.copyDirectoryToProject("resolve/" + getTestName(false), "")
    //assertResolvesTo(PyFunction::class.java, "append")
  }

  // PY-27863
  fun testAttributeClassLevelAnnotation() {
    TODO()
    //myFixture.copyDirectoryToProject("resolve/" + getTestName(false), "")
    //
    //val target = assertResolvesTo(PyTargetExpression::class.java, "some_attr")
    //
    //val file = myFixture.getFile()
    //val context = TypeEvalContext.codeAnalysis(myFixture.getProject(), file)
    //val resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context)
    //
    //// It's like an attempt to find type annotation for attribute on the class level.
    //val classType = PyClassTypeImpl(target.containingClass!!, true)
    //assertEmpty(classType.resolveMember(target.referencedName!!, target, AccessDirection.READ, resolveContext, true)!!)
    //
    //assertProjectFilesNotParsed(file)
    //assertSdkRootsNotParsed(file)
  }

  // PY-28228
  fun testReturnAnnotationForwardReference() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON37
    ) { assertResolvesTo(PyClass::class.java, "A") }
  }

  // PY-28228
  fun testParameterAnnotationForwardReference() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON37
    ) { assertResolvesTo(PyClass::class.java, "A") }
  }

  // PY-19890
  fun testUnboundVariableOnClassLevelDeclaredBelowAsTarget() {
    val foo = assertResolvesTo(PyTargetExpression::class.java, "foo")

    val value = foo.findAssignedValue()
    assertInstanceOf(value, PyStringLiteralExpression::class.java)
    assertEquals("global", (value as PyStringLiteralExpression).stringValue)
  }

  // PY-19890
  fun testUnboundVariableOnClassLevelDeclaredBelowAsMethod() {
    val foo = assertResolvesTo(PyTargetExpression::class.java, "foo")

    val value = foo.findAssignedValue()
    assertInstanceOf(value, PyStringLiteralExpression::class.java)
    assertEquals("global", (value as PyStringLiteralExpression).stringValue)
  }

  // PY-19890
  fun testUnboundVariableOnClassLevelDeclaredBelowAsClass() {
    val foo = assertResolvesTo(PyTargetExpression::class.java, "foo")

    val value = foo.findAssignedValue()
    assertInstanceOf(value, PyStringLiteralExpression::class.java)
    assertEquals("global", (value as PyStringLiteralExpression).stringValue)
  }

  // PY-19890
  fun testUnboundVariableOnClassLevelDeclaredBelowAsImport() {
    val foo = assertResolvesTo(PyTargetExpression::class.java, "foo")

    val value = foo.findAssignedValue()
    assertInstanceOf(value, PyStringLiteralExpression::class.java)
    assertEquals("global", (value as PyStringLiteralExpression).stringValue)
  }

  // PY-19890
  fun testUnboundVariableOnClassLevelDeclaredBelowAsImportWithAs() {
    val foo = assertResolvesTo(PyTargetExpression::class.java, "foo")

    val value = foo.findAssignedValue()
    assertInstanceOf(value, PyStringLiteralExpression::class.java)
    assertEquals("global", (value as PyStringLiteralExpression).stringValue)
  }

  // PY-29975
  fun testUnboundVariableOnClassLevelNotDeclaredBelow() {
    assertResolvesTo(PyNamedParameter::class.java, "foo")
  }

  // PY-30512
  fun testDunderBuiltins() {
    val element = doResolve()
    assertEquals(PyBuiltinCache.getInstance(myPsiFile).builtinsFile, element)
  }

  // PY-35531
  fun testOverloadedDunderInit() {
    val file = configureByFile("resolve/" + getTestName(false) + ".py") as PyFile
    val context = TypeEvalContext.codeAnalysis(myProject, file)

    val function = file.findTopLevelClass("A")!!.findInitOrNew(false, context)
    assertNotNull(function)
    assertFalse(PyiUtil.isOverload(function!!, context))
  }

  // PY-33886
  fun testAssignmentExpressions() {
    assertResolvesTo(
      "if a := b:\n" +
      "    print(a)\n" +
      "          <ref>",
      PyTargetExpression::class.java,
      "a"
    )

    assertResolvesTo(
      ("[y := 2, y**2]\n" + "         <ref>"),
      PyTargetExpression::class.java,
      "y"
    )

    assertResolvesTo(
      ("[y for x in data if (y := f(x))]\n" + " <ref>"),
      PyTargetExpression::class.java,
      "y"
    )

    assertResolvesTo(
      ("len(lines := [])\n" +
       "print(lines)\n" +
       "       <ref>"),
      PyTargetExpression::class.java,
      "lines"
    )
  }

  // PY-33886
  fun testAssignmentExpressionGoesToOuterScope() {
    assertResolvesTo(
      ("if any({(comment := line).startswith('#') for line in lines}):\n" +
       "    print(\"First comment:\", comment)\n" +
       "                             <ref>"),
      PyTargetExpression::class.java,
      "comment"
    )

    assertResolvesTo(
      ("if all((nonblank := line).strip() == '' for line in lines):\n" +
       "    pass\n" +
       "else:\n" +
       "    print(\"First non-blank line:\", nonblank)\n" +
       "                                     <ref>"),
      PyTargetExpression::class.java,
      "nonblank"
    )
  }

  // PY-33886
  fun testAssignmentExpressionGoesFromOuterScope() {
    assertResolvesTo(
      ("[[x * y for x in range(5)] for i in range(5) if (y := i)]\n" + "      <ref>"),
      PyTargetExpression::class.java,
      "y"
    )
  }

  // PY-33886
  fun testAssignmentExpressionsAndOuterVar() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON38
    ) {
      val results = multiResolve()

      val first = results[0].element
      assertResolveResult(first, PyTargetExpression::class.java, "total")
      assertInstanceOf(first?.parent, PyAssignmentStatement::class.java)

      val second = results[1].element
      assertResolveResult(second, PyTargetExpression::class.java, "total")
      assertInstanceOf(second?.parent, PyAssignmentExpression::class.java)
    }
  }

  // PY-38220
  fun testAssignedQNameForTargetInitializedWithSubscriptionExpression() {
    TODO()
    //val file = myFixture.configureByText(PythonFileType.INSTANCE, "import a\nt = a.b[c]") as PyFile
    //assertNull(file.findTopLevelAttribute("t")!!.assignedQName)
  }

  // PY-38220
  fun testResolvingAssignedValueForTargetInitializedWithSubscriptionExpression() {
    TODO()
    //val file = myFixture.configureByText(PythonFileType.INSTANCE, "import a\nt = a.b[c]") as PyFile
    //myFixture.addFileToProject("a.py", "b = {}  # type: dict") // specify type of `b` so `__getitem__` could be resolved
    //
    //val context = TypeEvalContext.codeInsightFallback(myFixture.getProject())
    //assertEmpty(file.findTopLevelAttribute("t")!!.multiResolveAssignedValue(
    //  PyResolveContext.noImplicits().withTypeEvalContext(context)))
  }
}