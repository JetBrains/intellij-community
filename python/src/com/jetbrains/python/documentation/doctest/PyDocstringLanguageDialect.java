package com.jetbrains.python.documentation.doctest;

import com.intellij.codeInsight.intention.impl.QuickEditAction;
import com.intellij.lang.InjectableLanguage;
import com.intellij.lang.Language;
import com.jetbrains.python.PythonLanguage;

/**
 * User : ktisha
 */
public class PyDocstringLanguageDialect extends Language implements InjectableLanguage {
  public static PyDocstringLanguageDialect getInstance() {
    return (PyDocstringLanguageDialect)PyDocstringFileType.INSTANCE.getLanguage();
  }

  protected PyDocstringLanguageDialect() {
    super(PythonLanguage.getInstance(), "PyDocstring");
    putUserData(QuickEditAction.EDIT_ACTION_AVAILABLE, false);
  }
}
