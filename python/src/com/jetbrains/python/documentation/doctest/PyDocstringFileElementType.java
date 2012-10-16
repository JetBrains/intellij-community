package com.jetbrains.python.documentation.doctest;

import com.intellij.lang.Language;
import com.jetbrains.python.psi.PyFileElementType;

/**
 * User : ktisha
 */
public class PyDocstringFileElementType extends PyFileElementType {
  public PyDocstringFileElementType(Language language) {
    super(language);
  }

  @Override
  public String getExternalId() {
    return "PyDocstring.FILE";
  }
}
