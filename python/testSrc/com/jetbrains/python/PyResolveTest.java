package com.jetbrains.python;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.fixtures.PyResolveTestCase;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;
import com.jetbrains.python.psi.resolve.ImportedResolveResult;

public class PyResolveTest extends PyResolveTestCase {
  @Override
  protected PsiElement doResolve() {
    myFixture.configureByFile("resolve/" + getTestName(false) + ".py");
    int offset = findMarkerOffset(myFixture.getFile());
    final PsiReference ref = myFixture.getFile().findReferenceAt(offset);
    return ref.resolve();
  }

  protected PsiElement resolve() {
    PsiReference ref = configureByFile("resolve/" + getTestName(false) + ".py");
    //  if need be: PythonLanguageLevelPusher.setForcedLanguageLevel(project, LanguageLevel.PYTHON26);
    return ref.resolve();
  }

  private ResolveResult[] multiResolve() {
    PsiReference ref = configureByFile("resolve/" + getTestName(false) + ".py");
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
    assertEquals(((PyFunction)target).getName(), PyNames.INIT);
  }

  public void testToConstructorInherited() {
    ResolveResult[] targets = multiResolve();
    assertEquals(2, targets.length); // to class, to init
    PsiElement elt;
    // class
    elt = targets[0].getElement();
    assertTrue(elt instanceof PyClass);
    assertEquals(((PyClass)elt).getName(), "Bar");
    // init
    elt = targets[1].getElement();
    assertTrue(elt instanceof PyFunction);
    PyFunction fun = (PyFunction)elt;
    assertEquals(fun.getName(), PyNames.INIT);
    PyClass cls = fun.getContainingClass();
    assertNotNull(cls);
    assertEquals(cls.getName(), "Foo");
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
    assertEquals(((PyTargetExpression)targetElement).getName(), "FOO");
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
    assertEquals("A", ((PyFunction) targetElement).getContainingClass().getName());
  }

  public void testSuperPy3k() {  // PY-1330
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), LanguageLevel.PYTHON30);
    try {
      final PyFunction pyFunction = assertResolvesTo(PyFunction.class, "foo");
      assertEquals("A", pyFunction.getContainingClass().getName());
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }
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
    assertTrue(resolveResults [0].getElement() instanceof PyFunction);
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
    assertInstanceOf(targetElement, PyFunction.class);
  }

  public void testUnresolvedImport() {
    final ResolveResult[] results = multiResolve();
    assertEquals(1, results.length);
    assertTrue(results [0] instanceof ImportedResolveResult);
    ImportedResolveResult result = (ImportedResolveResult) results [0];
    assertNull(result.getElement());
  }

  public void testIsInstance() {  // PY-1133
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyNamedParameter.class);
  }

  public void testListComprehension() { // PY-1143
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyTargetExpression.class);
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
    assertInstanceOf(element, PyTargetExpression.class);
    assertTrue(element.getParent() instanceof PySetCompExpression);
  }

  public void testListAssignment() {
    final PsiElement element = doResolve();
    assertInstanceOf(element, PyTargetExpression.class);
  }

  public void testStarUnpacking() {  // PY-1459
    assertResolvesTo(LanguageLevel.PYTHON30, PyTargetExpression.class, "heads");
  }

  public void testStarUnpackingInLoop() {  // PY-1525
    assertResolvesTo(LanguageLevel.PYTHON30, PyTargetExpression.class, "bbb");
  }

  public void testBuiltinVsClassMember() {  // PY-1654
    final PyFunction pyFunction = assertResolvesTo(PyFunction.class, "eval");
    assertEquals("__builtin__.py", pyFunction.getContainingFile().getName());
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
    assertResolvesTo(PyClass.class, "date", "datetime.py");
  }
}