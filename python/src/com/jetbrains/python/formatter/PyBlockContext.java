package com.jetbrains.python.formatter;

import com.intellij.formatting.FormattingMode;
import com.intellij.formatting.SpacingBuilder;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;

/**
 * @author yole
 */
public class PyBlockContext {
  private final CommonCodeStyleSettings mySettings;
  private final SpacingBuilder mySpacingBuilder;
  private final FormattingMode myMode;

  public PyBlockContext(CommonCodeStyleSettings settings, SpacingBuilder builder, FormattingMode mode) {
    mySettings = settings;
    mySpacingBuilder = builder;
    myMode = mode;
  }

  public CommonCodeStyleSettings getSettings() {
    return mySettings;
  }

  public SpacingBuilder getSpacingBuilder() {
    return mySpacingBuilder;
  }

  public FormattingMode getMode() {
    return myMode;
  }
}
