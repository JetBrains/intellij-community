// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.regexp;

import com.intellij.lang.Language;
import org.intellij.lang.regexp.RegExpLanguage;


public class PythonRegexpLanguage extends Language {
  public static final PythonRegexpLanguage INSTANCE = new PythonRegexpLanguage();

  public PythonRegexpLanguage() {
    super(RegExpLanguage.INSTANCE, "PythonRegExp");
  }
}
