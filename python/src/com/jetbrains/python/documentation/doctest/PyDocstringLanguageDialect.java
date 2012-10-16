package com.jetbrains.python.documentation.doctest;

import com.intellij.lang.Language;
import com.jetbrains.python.PythonLanguage;

/**
 * User : ktisha
 */
public class PyDocstringLanguageDialect extends Language {
  public static PyDocstringLanguageDialect getInstance() {
    return (PyDocstringLanguageDialect)PyDocstringFileType.INSTANCE.getLanguage();
  }

  protected PyDocstringLanguageDialect() {
    super(PythonLanguage.getInstance(), "PyDocstring");
  }
}
