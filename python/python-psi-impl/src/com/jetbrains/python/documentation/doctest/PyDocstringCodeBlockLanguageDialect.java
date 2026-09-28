// Copyright 2000-2026 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation.doctest;

import com.intellij.lang.DependentLanguage;
import com.intellij.lang.InjectableLanguage;
import com.intellij.lang.Language;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonLanguage;

/**
 * This is the language of a plain Python code block in a docstring. An rst {@code code-block}
 * directive or a Markdown fence produces this block. A {@code >>>} doctest is different. See
 * {@link PyDoctestLanguageDialect}. This dialect keeps the quick-edit popup on, because the block
 * has no doctest prompt to strip.
 */
public class PyDocstringCodeBlockLanguageDialect extends Language implements DependentLanguage, InjectableLanguage {

  public static PyDocstringCodeBlockLanguageDialect getInstance() {
    return (PyDocstringCodeBlockLanguageDialect)PyDocstringCodeBlockFileType.INSTANCE.getLanguage();
  }

  protected PyDocstringCodeBlockLanguageDialect() {
    super(PythonLanguage.getInstance(), PyNames.PY_DOCSTRING_CODE_BLOCK_ID);
  }
}
