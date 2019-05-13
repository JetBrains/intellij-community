package org.jetbrains.plugins.ipnb.psi;

import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.stubs.PyFunctionElementType;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;

public class IpnbPyTokenTypes {
  public static final PyElementType IPNB_REFERENCE = new PyElementType("IPNB_REFERENCE", IpnbPyReferenceExpression.class);
  public static final PyStubElementType<PyTargetExpressionStub, PyTargetExpression> IPNB_TARGET = new IpnbPyTargetExpressionElementType();
  public static final PyFunctionElementType IPNB_FUNCTION = new IpnbFunctionElementType();

  private IpnbPyTokenTypes() {
  }
}
