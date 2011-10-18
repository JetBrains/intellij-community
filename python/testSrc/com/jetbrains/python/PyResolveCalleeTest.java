package com.jetbrains.python;

import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.resolve.PyResolveContext;

import java.util.EnumSet;

/**
 * Tests callee resolution in PyCallExpressionImpl.
 * User: dcheryasov
 * Date: Aug 21, 2008
 */
public class PyResolveCalleeTest extends PyTestCase {

  private PyCallExpression.PyMarkedCallee resolveCallee() {
    PsiReference ref = myFixture.getReferenceAtCaretPosition("/resolve/callee/" + getTestName(false) + ".py");
    PyCallExpression call = PsiTreeUtil.getParentOfType(ref.getElement(), PyCallExpression.class);
    return call.resolveCallee(PyResolveContext.defaultContext());
  }

  public void testInstanceCall() {
    PyCallExpression.PyMarkedCallee resolved = resolveCallee();
    assertNotNull(resolved.getCallable());
    assertEquals(1, resolved.getImplicitOffset());
  }

  public void testClassCall() {
    PyCallExpression.PyMarkedCallee resolved = resolveCallee();
    assertNotNull(resolved.getCallable());
    assertTrue(resolved.getFlags().equals(EnumSet.noneOf(PyFunction.Flag.class)));
  }

  public void testDecoCall() {
    PyCallExpression.PyMarkedCallee resolved = resolveCallee();
    assertNotNull(resolved.getCallable());
    assertEquals(1, resolved.getImplicitOffset());
  }

  public void testDecoParamCall() {
    PyCallExpression.PyMarkedCallee resolved = resolveCallee();
    assertNotNull(resolved.getCallable());
    assertTrue(resolved.getFlags().equals(EnumSet.noneOf(PyFunction.Flag.class)));
  }
}
