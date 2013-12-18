package org.jetbrains.postfixCompletion.templates;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.util.Aliases;

@Aliases("nn")
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