/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.documentation.doctest;

import com.intellij.codeInsight.intention.impl.QuickEditActionKeys;
import com.intellij.lang.DependentLanguage;
import com.intellij.lang.Language;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonLanguage;

/**
 * User : ktisha
 */
public class PyDocstringLanguageDialect extends Language implements DependentLanguage {

  public static PyDocstringLanguageDialect getInstance() {
    return (PyDocstringLanguageDialect)PyDocstringFileType.INSTANCE.getLanguage();
  }

  protected PyDocstringLanguageDialect() {
    super(PythonLanguage.getInstance(), PyNames.PY_DOCSTRING_ID);
    putUserData(QuickEditActionKeys.EDIT_ACTION_AVAILABLE, false);
  }
}
