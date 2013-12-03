package org.jetbrains.postfixCompletion.templates;

import org.jetbrains.annotations.NotNull;

public class NotNullCheckPostfixTemplate extends NullCheckPostfixTemplate {
  public NotNullCheckPostfixTemplate() {
    super("notnull", "Checks expression to be not-null", "if (expr != null)");
  }

  @NotNull
  @Override
  protected String getTail() {
    return "!= null";
  }
}