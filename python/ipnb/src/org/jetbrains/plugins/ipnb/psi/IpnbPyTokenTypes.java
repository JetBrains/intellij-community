package org.jetbrains.plugins.ipnb.psi;

import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.impl.stubs.PyFunctionElementType;

public class IpnbPyTokenTypes {
  public static final PyElementType IPNB_REFERENCE = new PyElementType("IPNB_REFERENCE", IpnbPyReferenceExpression.class);
  public static final PyElementType IPNB_TARGET = new PyElementType("IPNB_TARGET", IpnbPyTargetExpression.class);
  public static final PyFunctionElementType IPNB_FUNCTION = new IpnbFunctionElementType();

  private IpnbPyTokenTypes() {
  }
}
