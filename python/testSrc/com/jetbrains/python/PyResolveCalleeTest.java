package com.jetbrains.python;

import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.fixtures.PyResolveTestCase;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyFunction;

import java.util.EnumSet;

/**
 * Tests callee resolution in PyCallExpressionImpl.
 * User: dcheryasov
 * Date: Aug 21, 2008
 */
public class PyResolveCalleeTest extends PyResolveTestCase {

  private PyCallExpression.PyMarkedCallee resolveCallee() throws Exception {
    PsiReference ref = configureByFile(getTestName(false) + ".py");
    PyCallExpression call = PsiTreeUtil.getParentOfType(ref.getElement(), PyCallExpression.class);
    return call.resolveCallee();
  }

  public void testInstanceCall() throws Exception {
    PyCallExpression.PyMarkedCallee resolved = resolveCallee();
    assertNotNull(resolved.getCallable());
    assertEquals(1, resolved.getImplicitOffset());
  }

  public void testClassCall() throws Exception {
    PyCallExpression.PyMarkedCallee resolved = resolveCallee();
    assertNotNull(resolved.getCallable());
    assertTrue(resolved.getFlags().equals(EnumSet.noneOf(PyFunction.Flag.class)));
  }

  public void testDecoCall() throws Exception {
    PyCallExpression.PyMarkedCallee resolved = resolveCallee();
    assertNotNull(resolved.getCallable());
    assertEquals(1, resolved.getImplicitOffset());
  }

  public void testDecoParamCall() throws Exception {
    PyCallExpression.PyMarkedCallee resolved = resolveCallee();
    assertNotNull(resolved.getCallable());
    assertTrue(resolved.getFlags().equals(EnumSet.noneOf(PyFunction.Flag.class)));
  }

  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/resolve/callee/";
  }
}
