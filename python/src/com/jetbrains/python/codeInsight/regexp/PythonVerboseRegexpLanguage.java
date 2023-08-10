// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.regexp;

import com.intellij.lang.Language;
import com.jetbrains.python.PyNames;
import org.intellij.lang.regexp.RegExpLanguage;


public class PythonVerboseRegexpLanguage extends Language {
  public static final PythonVerboseRegexpLanguage INSTANCE = new PythonVerboseRegexpLanguage();

  public PythonVerboseRegexpLanguage() {
    super(RegExpLanguage.INSTANCE, PyNames.VERBOSE_REG_EXP_LANGUAGE_ID);
  }
}
