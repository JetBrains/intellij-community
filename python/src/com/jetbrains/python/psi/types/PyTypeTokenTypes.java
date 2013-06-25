package com.jetbrains.python.psi.types;

import com.jetbrains.python.psi.PyElementType;

/**
 * @author vlan
 */
public class PyTypeTokenTypes {
  private PyTypeTokenTypes() {}

  public static final PyElementType NL = new PyElementType("NL");
  public static final PyElementType SPACE = new PyElementType("SPACE");
  public static final PyElementType MARKUP = new PyElementType("MARKUP");
  public static final PyElementType OP = new PyElementType("OP");
  public static final PyElementType PARAMETER = new PyElementType("PARAMETER");
  public static final PyElementType IDENTIFIER = new PyElementType("IDENTIFIER");
}
