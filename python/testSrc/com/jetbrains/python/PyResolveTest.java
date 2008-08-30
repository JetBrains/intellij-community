/*
 * User: anna
 * Date: 20-Feb-2008
 */
package com.jetbrains.python;

import com.intellij.openapi.application.PathManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.ResolveTestCase;
import com.jetbrains.python.psi.*;

public class PyResolveTest extends ResolveTestCase {
  private PsiElement resolve() throws Exception {
    PsiReference ref = configureByFile(getTestName(false) + ".py");
    return ref.resolve();
  }

  public void testClass() throws Exception {
    PsiElement target = resolve();
    assertTrue(target instanceof PyClass);
  }

  public void testFunc() throws Exception {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyFunction);
  }

  // NOTE: maybe this test does not belong exactly here; still it's the best place currently. 
  public void testComplexCallee() throws Exception {
    PsiElement targetElement = resolve();
    PyExpression assigned = ((PyAssignmentStatement) targetElement.getContext()).getAssignedValue();
    assertTrue(assigned instanceof PyCallExpression);
    PsiElement callee = ((PyCallExpression)assigned).getCallee();
    assertTrue(callee instanceof PySubscriptionExpression);
  }

  public void testVar() throws Exception {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testQualifiedFunc() throws Exception {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyFunction);
  }

  public void testQualifiedVar() throws Exception {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testInnerFuncVar() throws Exception {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testTupleInComprh() throws Exception {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testForStatement() throws Exception {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testExceptClause() throws Exception {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testLookAhead() throws Exception {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testLookAheadCapped() throws Exception {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testTryExceptElse() throws Exception {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testGlobal() throws Exception {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
    assertTrue(targetElement.getParent() instanceof PyAssignmentStatement);
  }

  public void testLambda() throws Exception {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyParameter);
  }

  public void testSuperField() throws Exception {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testFieldInCondition() throws Exception {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testMultipleFields() throws Exception {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testClassPeerMembers() throws Exception {
    PsiElement target = resolve();
    assertTrue(target instanceof PyFunction);
  }

  public void testTuple() throws Exception {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
    assertTrue(targetElement.getParent() instanceof PyAssignmentStatement);
  }

  public void testMultiTarget() throws Exception {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
    assertTrue(targetElement.getParent() instanceof PyAssignmentStatement);
  }

  public void testMultiTargetTuple() throws Exception {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyTargetExpression);
    assertNotNull(PsiTreeUtil.getParentOfType(targetElement, PyAssignmentStatement.class)); // it's deep in a tuple
  }



  @Override
  protected String getTestDataPath() {
    return PathManager.getHomePath() + "/plugins/python/testData/resolve/";
  }
}