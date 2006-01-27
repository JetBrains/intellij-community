package com.intellij.uiDesigner;

import com.intellij.uiDesigner.quickFixes.QuickFix;
import org.jetbrains.annotations.NotNull;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ErrorInfo {
  public static ErrorInfo[] EMPTY_ARRAY = new ErrorInfo[0];

  public final String myDescription;
  private final String myPropertyName;
  public final QuickFix[] myFixes;

  public ErrorInfo(final String propertyName, @NotNull final String description, @NotNull final QuickFix[] fixes) {
    myPropertyName = propertyName;
    myDescription = description;
    myFixes = fixes;
  }

  public String getPropertyName() {
    return myPropertyName;
  }
}
