package com.jetbrains.python;

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
    assertEquals(1, resolved.getImplicitOffset());
  }

  public void testClassCall() throws Exception {
    PyCallExpression.PyMarkedFunction resolved = resolveCallee();
    assertNotNull(resolved.getFunction());
    assertTrue(resolved.getFlags().equals(EnumSet.noneOf(PyCallExpression.Flag.class)));
  }

  public void testDecoCall() throws Exception {
    PyCallExpression.PyMarkedFunction resolved = resolveCallee();
    assertNotNull(resolved.getFunction());
    assertEquals(1, resolved.getImplicitOffset());
  }

  public void testDecoParamCall() throws Exception {
    PyCallExpression.PyMarkedFunction resolved = resolveCallee();
    assertNotNull(resolved.getFunction());
    assertTrue(resolved.getFlags().equals(EnumSet.noneOf(PyCallExpression.Flag.class)));
  }

  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/resolve/callee/";
  }
}
