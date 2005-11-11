package com.intellij.uiDesigner;

import com.intellij.uiDesigner.quickFixes.QuickFix;
import org.jetbrains.annotations.NotNull;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ErrorInfo {
  public final String myDescription;
  private final String myPropertyName;
  public final QuickFix[] myFixes;

  public ErrorInfo(final String propertyName, @NotNull final String description, @NotNull final QuickFix[] fixes) {
    myPropertyName = propertyName;
    //noinspection ConstantConditions
    if (description == null) {
      throw new IllegalArgumentException("description cannot be null");
    }
    //noinspection ConstantConditions
    if (fixes == null) {
      throw new IllegalArgumentException("fixes cannot be null");
    }
    myDescription = description;
    myFixes = fixes;
  }

  public String getPropertyName() {
    return myPropertyName;
  }
}
