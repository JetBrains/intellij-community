package com.jetbrains.python;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.fixtures.PyResolveTestCase;
import com.jetbrains.python.psi.*;

public class PyResolveTest extends PyResolveTestCase {
  private PsiElement resolve() {
    PsiReference ref = configureByFile("resolve/" + getTestName(false) + ".py");
    return ref.resolve();
  }

  private ResolveResult[] multiResolve() {
    PsiReference ref = configureByFile("resolve/" + getTestName(false) + ".py");
    assertTrue(ref instanceof PsiPolyVariantReference);
    return ((PsiPolyVariantReference)ref).multiResolve(false);
  }

  public void testClass() {
    PsiElement target = resolve();
    assertTrue(target instanceof PyClass);
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
    assertEquals(targets.length, 2); // to class, to init
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

  public void testStackOverflow() {
    PsiElement targetElement = resolve();
    assertNull(targetElement);
  }

  public void testProperty() {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
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
}