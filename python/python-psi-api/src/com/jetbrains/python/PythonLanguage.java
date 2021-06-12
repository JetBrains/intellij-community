// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.lang.Language;


public class PythonLanguage extends Language {

  public static final PythonLanguage INSTANCE = new PythonLanguage();

  public static PythonLanguage getInstance() {
    return INSTANCE;
  }

  protected PythonLanguage() {
    super("Python");
  }

  @Override
  public boolean isCaseSensitive() {
    return true; // http://jetbrains-feed.appspot.com/message/372001
  }
}
