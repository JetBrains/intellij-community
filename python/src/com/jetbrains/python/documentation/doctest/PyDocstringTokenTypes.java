package com.jetbrains.python.documentation.doctest;

import com.jetbrains.python.psi.PyElementType;

/**
 * User : ktisha
 */
public class PyDocstringTokenTypes {
  public static final PyElementType DOC_REFERENCE = new PyElementType("DOC_REFERENCE", PyDocReferenceExpression.class);
  public static final PyElementType DOTS = new PyElementType("DOTS");

  private PyDocstringTokenTypes() {
  }
}
