package com.jetbrains.python;

import com.intellij.openapi.application.PathManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.ResolveTestCase;
import com.jetbrains.python.psi.PyCallExpression;

import java.util.EnumSet;

/**
 * Tests callee resolution in PyCallExpressionImpl.
 * User: dcheryasov
 * Date: Aug 21, 2008
 */
public class PyResolveCalleeTest extends ResolveTestCase {

  private PyCallExpression.PyMarkedFunction resolveCallee() throws Exception {
    PsiReference ref = configureByFile(getTestName(false) + ".py");
    PyCallExpression call = PsiTreeUtil.getParentOfType(ref.getElement(), PyCallExpression.class);
    return call.resolveCallee();
  }

  public void testInstanceCall() throws Exception {
    PyCallExpression.PyMarkedFunction resolved = resolveCallee();
    assertNotNull(resolved.getFunction());
    assertTrue(resolved.getFlags().equals(EnumSet.of(PyCallExpression.Flag.IMPLICIT_FIRST_ARG)));
  }

  public void testClassCall() throws Exception {
    PyCallExpression.PyMarkedFunction resolved = resolveCallee();
    assertNotNull(resolved.getFunction());
    assertTrue(resolved.getFlags().equals(EnumSet.noneOf(PyCallExpression.Flag.class)));
  }

  @Override
  protected String getTestDataPath() {
    return PathManager.getHomePath() + "/plugins/python/testData/resolve/callee/";
  }
}
