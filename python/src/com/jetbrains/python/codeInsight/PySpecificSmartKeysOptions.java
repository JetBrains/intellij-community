package com.jetbrains.python.codeInsight;

import com.intellij.openapi.options.BeanConfigurable;
import com.intellij.openapi.options.UnnamedConfigurable;

/**
 * @author yole
 */
public class PySpecificSmartKeysOptions extends BeanConfigurable<PyCodeInsightSettings> implements UnnamedConfigurable {
  public PySpecificSmartKeysOptions() {
    super(PyCodeInsightSettings.getInstance());
    checkBox("INSERT_BACKSLASH_ON_WRAP", "Insert \\ when pressing Enter inside a statement");
    checkBox("INSERT_SELF_FOR_METHODS", "Insert 'self' when defining a method");
  }
}
