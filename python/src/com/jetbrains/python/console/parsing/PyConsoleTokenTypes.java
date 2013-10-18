package com.jetbrains.python.console.parsing;

import com.jetbrains.python.psi.PyElementType;

/**
 * @author traff
 */
public class PyConsoleTokenTypes {
  public static final PyElementType QUESTION_MARK = new PyElementType("QUESTION_MARK"); //?
  public static final PyElementType PLING = new PyElementType("PLING"); //!

  private PyConsoleTokenTypes() {
  }
}
