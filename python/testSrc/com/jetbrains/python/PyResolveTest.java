// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.fixtures.PyResolveTestCase;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.ImportedResolveResult;
import com.jetbrains.python.psi.types.TypeEvalContext;

public class PyResolveTest extends PyResolveTestCase {
  @Override
  protected PsiElement doResolve() {
    final PsiReference ref = findReferenceByMarker();
    return ref.resolve();
  }

  private PsiReference findReferenceByMarker() {
    myFixture.configureByFile("resolve/" + getTestName(false) + ".py");
    return PyResolveTestCase.findReferenceByMarker(myFixture.getFile());
  }

  protected PsiElement resolve() {
    PsiReference ref = configureByFile("resolve/" + getTestName(false) + ".py");
    //  if need be: PythonLanguageLevelPusher.setForcedLanguageLevel(project, LanguageLevel.PYTHON26);
    return ref.resolve();
  }

  private ResolveResult[] multiResolve() {
    PsiReference ref = findReferenceByMarker();
    assertTrue(ref instanceof PsiPolyVariantReference);
    return ((PsiPolyVariantReference)ref).multiResolve(false);
  }

  public void testClass() {
    assertResolvesTo(PyClass.class, "Test");
  }

  public void testFunc() {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyFunction);
  }

  public void testToConstructor() {
    PsiElement target = resolve();
    assertTrue(target instanceof PyFunction);
    assertEquals(PyNames.INIT, ((PyFunction)target).getName());
  }

  public void testInitOrNewReturnsInitWhenNewIsFirst() {
    doTestInitOrNewReturnsInit();
  }
  
  public void testInitOrNewReturnsInitWhenNewIsLast() {
    doTestInitOrNewReturnsInit();
  }

  private void doTestInitOrNewReturnsInit() {
    myFixture.configureByFile("resolve/" + getTestName(false) + ".py");
    final PyClass pyClass = PsiTreeUtil.findChildOfType(myFixture.getFile(), PyClass.class);
    assertNotNull(pyClass);
    final PyFunction init = pyClass.findInitOrNew(false, TypeEvalContext.userInitiated(myFixture.getProject(), myFixture.getFile()));
    assertEquals(PyNames.INIT, init.getName());
  }

  public void testToConstructorInherited() {
    ResolveResult[] targets = multiResolve();
    assertEquals(2, targets.length); // to class, to init
    PsiElement elt;
    // class
    elt = targets[0].getElement();
    assertTrue(elt instanceof PyClass);
    assertEquals("Bar", ((PyClass)elt).getName());
    // init
    elt = targets[1].getElement();
    assertTrue(elt instanceof PyFunction);
    PyFunction fun = (PyFunction)elt;
    assertEquals(PyNames.INIT, fun.getName());
    PyClass cls = fun.getContainingClass();
    assertNotNull(cls);
    assertEquals("Foo", cls.getName());
  }

  // NOTE: maybe this test does not belong exactly here; still it's the best place currently.
  public void testComplexCallee() {
    PsiElement targetElement = resolve();
    PyExpression assigned = ((PyAssignmentStatement)targetElement.getContext()).getAssignedValue();
    assertTrue(assigned instanceof PyCallExpression);
    PsiElement callee = ((PyCallExpression)assigned).getCallee();
    assertTrue(callee instanceof PySubscriptionExpression);
  }

  public void testVar() {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testDefaultInClass() {
    PsiElement targetElement = resolve();
    assertNotNull(targetElement);
    assertTrue(targetElement instanceof PyTargetExpression);
    assertEquals("FOO", ((PyTargetExpression)targetElement).getName());
  }

  public void testQualifiedFunc() {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyFunction);
  }

  public void testQualifiedVar() {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testQualifiedTarget() {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testQualifiedFalseTarget() {
    PsiElement targetElement = resolve();
    assertNull(targetElement);
  }

  public void testInnerFuncVar() {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testTupleInComprh() {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testForStatement() {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testExceptClause() {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testLookAhead() {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testLookAheadCapped() {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testTryExceptElse() {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testGlobal() {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
    assertTrue(targetElement.getParent() instanceof PyAssignmentStatement);
  }

  public void testGlobalInNestedFunction() {
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyTargetExpression.class);
    assertInstanceOf(ScopeUtil.getScopeOwner(targetElement), PyFile.class);
  }

  public void testGlobalDefinedLocally() {
    final PsiElement element = resolve();
    UsefulTestCase.assertInstanceOf(element, PyTargetExpression.class);
    final PsiElement parent = element.getParent();
    UsefulTestCase.assertInstanceOf(parent, PyAssignmentStatement.class);
  }

  public void testLambda() {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyNamedParameter);
  }

  public void testLambdaParameterOutside() {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testSuperField() {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testFieldInCondition() {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testMultipleFields() {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testClassPeerMembers() {
    PsiElement target = resolve();
    assertTrue(target instanceof PyFunction);
  }

  public void testTuple() {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
    assertTrue(targetElement.getParent() instanceof PyAssignmentStatement);
  }

  public void testMultiTarget() {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
    assertTrue(targetElement.getParent() instanceof PyAssignmentStatement);
  }

  public void testMultiTargetTuple() {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
    assertNotNull(PsiTreeUtil.getParentOfType(targetElement, PyAssignmentStatement.class)); // it's deep in a tuple
  }

  public void testWithStatement() {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
    assertTrue(targetElement.getParent() instanceof PyWithItem);
  }

  public void testTupleInExcept() {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
    assertTrue(PsiTreeUtil.getParentOfType(targetElement, PyExceptPart.class) != null);
  }


  public void testDocStringClass() {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyStringLiteralExpression);
    assertEquals("Docstring of class Foo", ((PyStringLiteralExpression)targetElement).getStringValue());
  }

  public void testDocStringInstance() {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyStringLiteralExpression);
    assertEquals("Docstring of class Foo", ((PyStringLiteralExpression)targetElement).getStringValue());
  }

  public void testDocStringFunction() {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyStringLiteralExpression);
    assertEquals("Docstring of function bar", ((PyStringLiteralExpression)targetElement).getStringValue());
  }

  public void testDocStringInvalid() {
    PsiElement targetElement = resolve();
    assertNull(targetElement);
  }

  public void testFieldNotInInit() {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testClassIsNotMemberOfItself() {
    PsiElement targetElement = resolve();
    assertNull(targetElement);
  }

  public void testSuper() {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyFunction);
    assertEquals("A", ((PyFunction)targetElement).getContainingClass().getName());
  }

  public void testSuperPy3k() {  // PY-1330
    runWithLanguageLevel(
      LanguageLevel.PYTHON30,
      () -> {
        final PyFunction pyFunction = assertResolvesTo(PyFunction.class, "foo");
        assertEquals("A", pyFunction.getContainingClass().getName());
      }
    );
  }

  public void testStackOverflow() {
    PsiElement targetElement = resolve();
    assertNull(targetElement);
  }

  public void testProperty() {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyFunction);
    assertEquals("set_full_name", ((PyFunction)targetElement).getName());
  }

  public void testLambdaWithParens() {  // PY-882
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyParameter);
  }

  public void testTextBasedResolve() {
    ResolveResult[] resolveResults = multiResolve();
    assertEquals(1, resolveResults.length);
    assertTrue(resolveResults[0].getElement() instanceof PyFunction);
  }

  public void testClassPrivateInClass() {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
    assertTrue(targetElement.getParent() instanceof PyAssignmentStatement);
  }

  public void testClassPrivateInMethod() {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
    assertTrue(targetElement.getParent() instanceof PyAssignmentStatement);
  }

  public void testClassPrivateInMethodNested() {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
    assertTrue(targetElement.getParent() instanceof PyAssignmentStatement);
  }

  public void testClassPrivateInherited() {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
    assertTrue(targetElement.getParent() instanceof PyAssignmentStatement);
  }

  public void testClassPrivateOutsideClass() {
    PsiElement targetElement = resolve();
    assertNull(targetElement);
  }

  public void testClassPrivateOutsideInstance() {
    PsiElement targetElement = resolve();
    assertNull(targetElement);
  }

  public void testClassNameEqualsMethodName() {
    PsiElement targetElement = resolve();
    UsefulTestCase.assertInstanceOf(targetElement, PyFunction.class);
  }

  public void testUnresolvedImport() {
    final ResolveResult[] results = multiResolve();
    assertEquals(1, results.length);
    UsefulTestCase.assertInstanceOf(results[0], ImportedResolveResult.class);
    ImportedResolveResult result = (ImportedResolveResult) results [0];
    assertNull(result.getElement());
  }

  public void testIsInstance() {  // PY-1133
    PsiElement targetElement = resolve();
    UsefulTestCase.assertInstanceOf(targetElement, PyNamedParameter.class);
  }

  public void testListComprehension() { // PY-1143
    PsiElement targetElement = resolve();
    UsefulTestCase.assertInstanceOf(targetElement, PyTargetExpression.class);
  }

  public void testSuperMetaClass() {
    assertResolvesTo(PyFunction.class, "foo");
  }

  public void testSuperDunderClass() {  // PY-1190
    assertResolvesTo(PyFunction.class, "foo");
  }

  public void testSuperTwoClasses() {  // PY-2133
    final PyFunction pyFunction = assertResolvesTo(PyFunction.class, "my_call");
    assertEquals("Base2", pyFunction.getContainingClass().getName());
  }

  public void testLambdaDefaultParameter() {
    final PsiElement element = doResolve();
    UsefulTestCase.assertInstanceOf(element, PyTargetExpression.class);
    assertTrue(element.getParent() instanceof PySetCompExpression);
  }

  public void testListAssignment() {
    final PsiElement element = doResolve();
    UsefulTestCase.assertInstanceOf(element, PyTargetExpression.class);
  }

  public void testStarUnpacking() {  // PY-1459
    assertResolvesTo(LanguageLevel.PYTHON30, PyTargetExpression.class, "heads");
  }

  public void testStarUnpackingInLoop() {  // PY-1525
    assertResolvesTo(LanguageLevel.PYTHON30, PyTargetExpression.class, "bbb");
  }

  public void testBuiltinVsClassMember() {  // PY-1654
    final PyFunction pyFunction = assertResolvesTo(PyFunction.class, "eval");
    assertIsBuiltin(pyFunction);
  }

  public void testLambdaToClass() {  // PY-2182
    assertResolvesTo(PyClass.class, "TestTwo");
  }

  public void testImportInTryExcept() {  // PY-2197
    assertResolvesTo(PyFile.class, "sys.py");
  }

  public void testModuleToBuiltins() {
    final PsiElement element = doResolve();
    assertNull(element);
  }

  public void testWithParentheses() {
    assertResolvesTo(LanguageLevel.PYTHON27, PyTargetExpression.class, "MockClass1");
  }

  public void testPrivateInsideModule() {  // PY-2618
    assertResolvesTo(PyClass.class, "__VeryPrivate");
  }

  public void testRedeclaredInstanceVar() {  // PY-2740
    assertResolvesTo(PyFunction.class, "jobsDoneCount");
  }

  public void testNoResolveIntoGenerator() {  // PY-3030
    PyTargetExpression expr = assertResolvesTo(PyTargetExpression.class, "foo");
    assertEquals("foo = 1", expr.getParent().getText());
  }

  public void testResolveInGenerator() {
    assertResolvesTo(PyTargetExpression.class, "foo");
  }

  public void testNestedListComp() {  // PY-3068
    assertResolvesTo(PyTargetExpression.class, "yy");
  }

  public void testSuperclassResolveScope() {  // PY-3554
    assertResolvesTo(PyClass.class, "date", "datetime.pyi");
  }

  public void testDontResolveTargetToBuiltins() {  // PY-4256
    assertResolvesTo(PyTargetExpression.class, "str");
  }

  public void testKeywordArgument() {
    assertResolvesTo(PyNamedParameter.class, "bar");
  }

  public void testImplicitResolveInstanceAttribute() {
    ResolveResult[] resolveResults = multiResolve();
    assertEquals(1, resolveResults.length);
    final PsiElement psiElement = resolveResults[0].getElement();
    assertTrue(psiElement instanceof PyTargetExpression && "xyzzy".equals(((PyTargetExpression)psiElement).getName()));
  }

  public void testAttributeAssignedNearby() {
    assertResolvesTo(PyTargetExpression.class, "xyzzy");
  }

  public void testPreviousTarget() {
    PsiElement resolved = resolve();
    UsefulTestCase.assertInstanceOf(resolved, PyTargetExpression.class);
    PyTargetExpression target = (PyTargetExpression)resolved;
    PyExpression value = target.findAssignedValue();
    UsefulTestCase.assertInstanceOf(value, PyNumericLiteralExpression.class);
  }

  public void testMetaclass() {
    final PyFunction function = assertResolvesTo(PyFunction.class, "getStore");
    assertEquals("PluginMetaclass", function.getContainingClass().getName());
  }

  // PY-6083
  public void testLambdaParameterInDecorator() {
    assertResolvesTo(PyNamedParameter.class, "xx");
  }

  // PY-6435
  public void testLambdaParameterInDefaultValue() {
    assertResolvesTo(PyNamedParameter.class, "xx");
  }

  // PY-6540
  public void testClassRedefinedField() {
    assertResolvesTo(PyClass.class, "Foo");
  }

  public void testKWArg() {
    assertResolvesTo(PyClass.class, "timedelta");
  }

  public void testShadowingTargetExpression() {
    assertResolvesTo(PyTargetExpression.class, "lab");
  }

  public void testReferenceInDocstring() {
    assertResolvesTo(PyClass.class, "datetime");
  }
  
  // PY-9795
  public void testGoogleDocstringParamType() {
    runWithDocStringFormat(DocStringFormat.GOOGLE, () -> assertResolvesTo(PyClass.class, "datetime"));
  }
  
  // PY-9795
  public void testGoogleDocstringReturnType() {
    runWithDocStringFormat(DocStringFormat.GOOGLE, () -> assertResolvesTo(PyClass.class, "MyClass"));
  }

  // PY-16906
  public void testGoogleDocstringModuleAttribute() {
    runWithDocStringFormat(DocStringFormat.GOOGLE, () -> assertResolvesTo(PyTargetExpression.class, "module_level_variable1"));
  }

  // PY-7541
  public void testLoopToUpperReassignment() {
    final PsiReference ref = findReferenceByMarker();
    final PsiElement source = ref.getElement();
    final PsiElement target = ref.resolve();
    assertNotNull(target);
    assertTrue(source != target);
    assertTrue(PyPsiUtils.isBefore(target, source));
  }

  // PY-7541
  public void testLoopToLowerReassignment() {
    final PsiReference ref = findReferenceByMarker();
    final PsiElement source = ref.getElement();
    final PsiElement target = ref.resolve();
    assertNotNull(target);
    assertTrue(source == target);
  }

  // PY-7970
  public void testAugmentedAssignment() {
    assertResolvesTo(PyTargetExpression.class, "foo");
  }

  // PY-7970
  public void testAugmentedAfterAugmented() {
    final PsiReference ref = findReferenceByMarker();
    final PsiElement source = ref.getElement();
    final PsiElement resolved = ref.resolve();
    UsefulTestCase.assertInstanceOf(resolved, PyReferenceExpression.class);
    assertNotSame(resolved, source);
    final PyReferenceExpression res = (PyReferenceExpression)resolved;
    assertNotNull(res);
    assertEquals("foo", res.getName());
    UsefulTestCase.assertInstanceOf(res.getParent(), PyAugAssignmentStatement.class);
  }

  public void testGeneratorShadowing() {  // PY-8725
    assertResolvesTo(PyFunction.class, "_");
  }

  // PY-6805
  public void testAttributeDefinedInNew() {
    final PsiElement resolved = resolve();
    UsefulTestCase.assertInstanceOf(resolved, PyTargetExpression.class);
    final PyTargetExpression target = (PyTargetExpression)resolved;
    assertEquals("foo", target.getName());
    final ScopeOwner owner = ScopeUtil.getScopeOwner(target);
    assertNotNull(owner);
    UsefulTestCase.assertInstanceOf(owner, PyFunction.class);
    assertEquals("__new__", owner.getName());
  }

  public void testPreferInitForAttributes() {  // PY-9228
    PyTargetExpression xyzzy = assertResolvesTo(PyTargetExpression.class, "xyzzy");
    assertEquals("__init__", PsiTreeUtil.getParentOfType(xyzzy, PyFunction.class).getName());
  }

  // PY-11401
  public void testResolveAttributesUsingOldStyleMROWhenUnresolvedAncestorsAndC3Fails() {
    assertResolvesTo(PyFunction.class, "foo");
  }

  // PY-15390
  public void testMatMul() {
    assertResolvesTo(PyFunction.class, "__matmul__");
  }

  // PY-15390
  public void testRMatMul() {
    assertResolvesTo(PyFunction.class, "__rmatmul__");
  }

  //PY-2748
  public void testFormatStringKWArgs() {
    PsiElement target = resolve();
    assertTrue(target instanceof  PyNumericLiteralExpression);
    assertTrue(12 == ((PyNumericLiteralExpression)target).getLongValue());
  }

  //PY-2748
  public void testFormatPositionalArgs() {
    PsiElement target = resolve();
    assertInstanceOf(target,  PyReferenceExpression.class);
    assertEquals("string", target.getText());
  }

  //PY-2748
  public void testFormatArgsAndKWargs() {
    PsiElement target = resolve();
    assertInstanceOf(target, PyStringLiteralExpression.class);
  }

  //PY-2748
  public void testFormatArgsAndKWargs1() {
    PsiElement target = resolve();
    assertTrue(target instanceof  PyStringLiteralExpression);
    assertEquals("keyword", ((PyStringLiteralExpression)target).getStringValue());
  }

  //PY-2748
  public void testFormatStringWithPackedDictAsArgument() {
    PsiElement target = resolve();
    assertTrue(target instanceof  PyStringLiteralExpression);
    assertEquals("\"f\"", target.getText());    
  }

  //PY-2748
  public void testFormatStringWithPackedListAsArgument() {
    PsiElement target = resolve();
    assertInstanceOf(target, PyNumericLiteralExpression.class);
    assertEquals("1", target.getText());
  }

  //PY-2748
  public void testFormatStringWithPackedTupleAsArgument() {
    PsiElement target = resolve();
    assertInstanceOf(target, PyStringLiteralExpression.class);
    assertEquals("\"snd\"", target.getText());
  }

  //PY-2748
  public void testFormatStringWithRefAsArgument() {
    PsiElement target = resolve();
    assertInstanceOf(target, PyStarArgument.class);
  }
  

  //PY-2748
  public void testPercentPositionalArgs() {
    PsiElement target = resolve();
    assertTrue(target instanceof PyStringLiteralExpression);
  }

  //PY-2748
  public void testPercentKeyWordArgs() {
    PsiElement target = resolve();
    assertTrue(target instanceof PyNumericLiteralExpression);
    assertNotNull(((PyNumericLiteralExpression)target).getLongValue());
    assertEquals(Long.valueOf(4181), ((PyNumericLiteralExpression)target).getLongValue());
  }

  public void testPercentStringKeyWordArgWithParentheses() {
    PsiElement target = resolve();
    assertTrue(target instanceof PyStringLiteralExpression);
    assertEquals("s", ((PyStringLiteralExpression)target).getStringValue());    
  }

  //PY-2748
  public void testPercentStringBinaryStatementArg() {
    PsiElement target = resolve();
    assertTrue(target instanceof PyStringLiteralExpression);
    assertEquals("1", ((PyStringLiteralExpression)target).getStringValue());
  }

  //PY-2748
  public void testPercentStringArgWithRedundantParentheses() {
    PsiElement target = resolve();
    assertTrue(target instanceof PyStringLiteralExpression);
    assertEquals("1", ((PyStringLiteralExpression)target).getStringValue());
  }

  //PY-2748
  public void testPercentStringWithRefAsArgument() {
    PsiElement target = resolve();
    assertEquals("tuple", target.getText());    
  }

  //PY-2748
  public void testPercentStringWithOneStringArgument() {
    PsiElement target = resolve();
    assertEquals("hello", ((PyStringLiteralExpression)target).getStringValue());
  }

  //PY-2748
  public void testFormatStringPackedDictCall() {
    PsiElement target = resolve();
    assertInstanceOf(target, PyKeywordArgument.class);
  }

  //PY-2748
  public void testPercentStringDictCall() {
    PsiElement target = resolve();
    assertEquals("hello", ((PyStringLiteralExpression)((PyKeywordArgument)target).getValueExpression()).getStringValue());
  }

  // PY-2748
  public void testPercentStringParenDictCall() {
    PsiElement target = resolve();
    assertEquals("hello", ((PyStringLiteralExpression)((PyKeywordArgument)target).getValueExpression()).getStringValue());
  }
  
  // PY-2748
  public void testPercentStringPosParenDictCall() {
    PsiElement target = resolve();
    assertInstanceOf(target, PyCallExpression.class);
    assertEquals("dict()", target.getText());
  }

  // PY-2748
  public void testFormatStringWithPackedAndNonPackedArgs() {
    PsiElement target = resolve();
    assertInstanceOf(target, PyNumericLiteralExpression.class);
    assertEquals("2", target.getText());
  }


  public void testGlobalNotDefinedAtTopLevel() {
    assertResolvesTo(PyTargetExpression.class, "foo");
  }

  // PY-13734
  public void testImplicitDunderClass() {
    assertUnresolved();
  }

  public void testImplicitDunderDoc() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);
    assertIsBuiltin(expression);
  }

  public void testImplicitDunderSizeOf() {
    assertUnresolved();
  }

  public void testImplicitUndeclaredClassAttr() {
    assertUnresolved();
  }

  public void testImplicitQualifiedClassAttr() {
    ResolveResult[] resolveResults = multiResolve();
    assertEquals(1, resolveResults.length);
    PyTargetExpression target = assertInstanceOf(resolveResults[0].getElement(), PyTargetExpression.class);
    assertEquals("CLASS_ATTR", target.getName());
    assertInstanceOf(ScopeUtil.getScopeOwner(target), PyClass.class);
  }

  // PY-13734
  public void testImplicitDunderClassNewStyleClass() {
    assertUnresolved();
  }

  public void testImplicitDunderDocNewStyleClass() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);
    assertIsBuiltin(expression);
  }

  public void testImplicitDunderSizeOfNewStyleClass() {
    assertUnresolved();
  }

  public void testImplicitUndeclaredClassAttrNewStyleClass() {
    assertUnresolved();
  }

  // PY-13734
  public void testImplicitDunderClassWithClassAttr() {
    assertUnresolved();
  }

  public void testImplicitDunderDocWithClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);
    assertIsBuiltin(expression);
  }

  public void testImplicitDunderSizeOfWithClassAttr() {
    assertUnresolved();
  }

  public void testImplicitClassAttr() {
    assertUnresolved();
  }

  // PY-13734
  public void testImplicitDunderClassWithClassAttrNewStyleClass() {
    assertUnresolved();
  }

  public void testImplicitDunderDocWithClassAttrNewStyleClass() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);
    assertIsBuiltin(expression);
  }

  public void testImplicitDunderSizeOfWithClassAttrNewStyleClass() {
    assertUnresolved();
  }

  public void testImplicitClassAttrNewStyleClass() {
    assertUnresolved();
  }

  // PY-13734
  public void testImplicitDunderClassWithInheritedClassAttr() {
    assertUnresolved();
  }

  public void testImplicitDunderDocWithInheritedClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);
    assertIsBuiltin(expression);
  }

  public void testImplicitDunderSizeOfWithInheritedClassAttr() {
    assertUnresolved();
  }

  public void testImplicitInheritedClassAttr() {
    assertUnresolved();
  }

  // PY-13734
  public void testInstanceDunderClass() {
    assertResolvesTo(PyClass.class, "A");
  }

  public void testInstanceDunderDoc() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);
    assertIsBuiltin(expression);
  }

  public void testInstanceDunderSizeOf() {
    assertUnresolved();
  }

  public void testInstanceUndeclaredClassAttr() {
    assertUnresolved();
  }

  // PY-13734
  public void testInstanceDunderClassNewStyleClass() {
    assertResolvesTo(PyClass.class, "A");
  }

  public void testInstanceDunderDocNewStyleClass() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);
    assertIsBuiltin(expression);
  }

  public void testInstanceDunderSizeOfNewStyleClass() {
    final PyFunction expression = assertResolvesTo(PyFunction.class, PyNames.SIZEOF);
    assertIsBuiltin(expression);
  }

  public void testInstanceUndeclaredClassAttrNewStyleClass() {
    assertUnresolved();
  }

  // PY-13734
  public void testInstanceDunderClassWithClassAttr() {
    assertResolvesTo(PyClass.class, "A");
  }

  public void testInstanceDunderDocWithClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  public void testInstanceDunderSizeOfWithClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.SIZEOF);

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  public void testInstanceClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, "my_attr");

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  // PY-13734
  public void testInstanceDunderClassWithClassAttrNewStyleClass() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.__CLASS__);

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  public void testInstanceDunderDocWithClassAttrNewStyleClass() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  public void testInstanceDunderSizeOfWithClassAttrNewStyleClass() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.SIZEOF);

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  public void testInstanceClassAttrNewStyleClass() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, "my_attr");

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  // PY-13734
  public void testInstanceDunderClassWithInheritedClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.__CLASS__);

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  public void testInstanceDunderDocWithInheritedClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);
    assertIsBuiltin(expression);
  }

  public void testInstanceDunderSizeOfWithInheritedClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.SIZEOF);

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  public void testInstanceInheritedClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, "my_attr");

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  // PY-13734
  public void testLocalDunderClass() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.__CLASS__);

    final PyFunction function = PsiTreeUtil.getParentOfType(expression, PyFunction.class);
    assertNotNull(function);
    assertEquals("foo", function.getName());
  }

  // PY-13734
  public void testTypeDunderClass() {
    assertUnresolved();
  }

  public void testTypeDunderDoc() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);
    assertIsBuiltin(expression);
  }

  public void testTypeDunderSizeOf() {
    assertUnresolved();
  }

  public void testTypeUndeclaredClassAttr() {
    assertUnresolved();
  }

  // PY-13734
  public void testTypeDunderClassNewStyleClass() {
    assertResolvesTo(PyClass.class, "type");
  }

  public void testTypeDunderDocNewStyleClass() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);
    assertIsBuiltin(expression);
  }

  public void testTypeDunderSizeOfNewStyleClass() {
    final PyFunction expression = assertResolvesTo(PyFunction.class, PyNames.SIZEOF);
    assertIsBuiltin(expression);
  }

  public void testTypeUndeclaredClassAttrNewStyleClass() {
    assertUnresolved();
  }

  // PY-13734
  public void testTypeDunderClassWithClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.__CLASS__);

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  public void testTypeDunderDocWithClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  public void testTypeDunderSizeOfWithClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.SIZEOF);

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  public void testTypeClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, "my_attr");

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  // PY-13734
  public void testTypeDunderClassWithClassAttrNewStyleClass() {
    assertResolvesTo(PyClass.class, "type");
  }

  public void testTypeDunderDocWithClassAttrNewStyleClass() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  public void testTypeDunderSizeOfWithClassAttrNewStyleClass() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.SIZEOF);

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  public void testTypeClassAttrNewStyleClass() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, "my_attr");

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  // PY-13734
  public void testTypeDunderClassWithInheritedClassAttr() {
    assertResolvesTo(PyClass.class, "type");
  }

  public void testTypeDunderDocWithInheritedClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);
    assertIsBuiltin(expression);
  }

  public void testTypeDunderSizeOfWithInheritedClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.SIZEOF);

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  public void testTypeInheritedClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, "my_attr");

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  // PY-13734
  public void testDunderClassInDeclaration() {
    assertUnresolved();
  }

  public void testDunderDocInDeclaration() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);
    assertIsBuiltin(expression);
  }

  public void testDunderSizeOfInDeclaration() {
    assertUnresolved();
  }

  public void testUndeclaredClassAttrInDeclaration() {
    assertUnresolved();
  }

  // PY-13734
  public void testDunderClassInDeclarationNewStyleClass() {
    assertUnresolved();
  }

  public void testDunderDocInDeclarationNewStyleClass() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);
    assertIsBuiltin(expression);
  }

  public void testDunderSizeOfInDeclarationNewStyleClass() {
    assertUnresolved();
  }

  public void testUndeclaredClassAttrInDeclarationNewStyleClass() {
    assertUnresolved();
  }

  // PY-13734
  public void testDunderClassInDeclarationWithClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.__CLASS__);

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  public void testDunderDocInDeclarationWithClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  public void testDunderSizeOfInDeclarationWithClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.SIZEOF);

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  public void testClassAttrInDeclaration() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, "my_attr");

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  // PY-13734
  public void testDunderClassInDeclarationWithClassAttrNewStyleClass() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.__CLASS__);

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  public void testDunderDocInDeclarationWithClassAttrNewStyleClass() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  public void testDunderSizeOfInDeclarationWithClassAttrNewStyleClass() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.SIZEOF);

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  public void testClassAttrInDeclarationNewStyleClass() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, "my_attr");

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  // PY-13734
  public void testDunderClassInDeclarationWithInheritedClassAttr() {
    assertUnresolved();
  }

  public void testDunderDocInDeclarationWithInheritedClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);
    assertIsBuiltin(expression);
  }

  public void testDunderSizeOfInDeclarationWithInheritedClassAttr() {
    assertUnresolved();
  }

  public void testInheritedClassAttrInDeclaration() {
    assertUnresolved();
  }

  // PY-13734
  public void testDunderClassInDeclarationInsideFunction() {
    assertUnresolved();
  }

  // PY-22763
  public void testComparisonOperatorReceiver() {
    final PsiElement element = doResolve();
    final PyFunction dunderLt = assertInstanceOf(element, PyFunction.class);
    assertEquals("__lt__", dunderLt.getName());
    assertEquals("str", dunderLt.getContainingClass().getName());
  }

  // PY-26006
  public void testSOEDecoratingFunctionWithSameNameDecorator() {
    final PyFunction function = assertInstanceOf(doResolve(), PyFunction.class);
    assertEquals(4, function.getTextOffset());
  }
}
