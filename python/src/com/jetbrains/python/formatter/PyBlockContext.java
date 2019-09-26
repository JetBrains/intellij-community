// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.formatter;

import com.intellij.formatting.FormattingMode;
import com.intellij.formatting.SpacingBuilder;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.jetbrains.python.PythonLanguage;

/**
 * @author yole
 */
public class PyBlockContext {
  private final CommonCodeStyleSettings mySettings;
  private final PyCodeStyleSettings myPySettings;
  private final SpacingBuilder mySpacingBuilder;
  private final FormattingMode myMode;

  public PyBlockContext(CodeStyleSettings settings, SpacingBuilder builder, FormattingMode mode) {
    mySettings = settings.getCommonSettings(PythonLanguage.getInstance());
    myPySettings = settings.getCustomSettings(PyCodeStyleSettings.class);
    mySpacingBuilder = builder;
    myMode = mode;
  }

  public CommonCodeStyleSettings getSettings() {
    return mySettings;
  }

  public PyCodeStyleSettings getPySettings() {
    return myPySettings;
  }

  public SpacingBuilder getSpacingBuilder() {
    return mySpacingBuilder;
  }

  public FormattingMode getMode() {
    return myMode;
  }
}
