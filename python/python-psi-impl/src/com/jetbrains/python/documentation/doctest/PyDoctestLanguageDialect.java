// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.documentation.doctest;

import com.intellij.codeInsight.intention.impl.QuickEditActionKeys;
import com.intellij.lang.DependentLanguage;
import com.intellij.lang.InjectableLanguage;
import com.intellij.lang.Language;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonLanguage;
import org.jetbrains.annotations.ApiStatus;

/**
 * User : ktisha
 */
@ApiStatus.Internal
public class PyDoctestLanguageDialect extends Language implements DependentLanguage, InjectableLanguage {

  public static PyDoctestLanguageDialect getInstance() {
    return (PyDoctestLanguageDialect)PyDoctestFileType.INSTANCE.getLanguage();
  }

  protected PyDoctestLanguageDialect() {
    super(PythonLanguage.getInstance(), PyNames.PY_DOCSTRING_ID);
    putUserData(QuickEditActionKeys.EDIT_ACTION_AVAILABLE, false);
  }
}
