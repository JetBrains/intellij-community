package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.options.BeanConfigurable;
import com.intellij.openapi.options.UnnamedConfigurable;

/**
 * @author yole
 */
public class PySmartKeysOptions extends BeanConfigurable<CodeInsightSettings> implements UnnamedConfigurable {
  public PySmartKeysOptions() {
    super(CodeInsightSettings.getInstance());
    checkBox("INDENT_TO_CARET_ON_PASTE", "Smart indent pasted lines");
  }
}
