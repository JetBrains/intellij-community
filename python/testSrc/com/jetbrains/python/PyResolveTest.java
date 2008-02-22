/*
 * User: anna
 * Date: 20-Feb-2008
 */
package com.jetbrains.python;

import com.intellij.openapi.application.PathManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.ResolveTestCase;
import ru.yole.pythonid.psi.PyClass;
import ru.yole.pythonid.psi.PyFunction;
import ru.yole.pythonid.psi.PyReferenceExpression;

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

  public void testVar() throws Exception {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyReferenceExpression);
  }

  public void testQualifiedFunc() throws Exception {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyFunction);
  }

  public void testQualifiedVar() throws Exception {
    PsiElement targetElement = resolve();
    assertTrue(targetElement instanceof PyReferenceExpression);
  }

  @Override
  protected String getTestDataPath() {
    return PathManager.getHomePath() + "/plugins/python/testData/resolve/";
  }
}