package com.intellij.uiDesigner;

import com.intellij.uiDesigner.quickFixes.QuickFix;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ErrorInfo {
  public final String myDescription;
  public final QuickFix[] myFixes;

  public ErrorInfo(final String description, final QuickFix[] fixes) {
    if (description == null) {
      throw new IllegalArgumentException("description cannot be null");
    }
    if (fixes == null) {
      throw new IllegalArgumentException("fixes cannot be null");
    }
    myDescription = description;
    myFixes = fixes;
  }
}
