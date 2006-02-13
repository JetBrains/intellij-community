package com.intellij.uiDesigner;

import com.intellij.uiDesigner.quickFixes.QuickFix;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
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
  private final HighlightDisplayLevel myHighlightDisplayLevel;

  public ErrorInfo(final String propertyName, @NotNull final String description,
                   @NotNull HighlightDisplayLevel highlightDisplayLevel, @NotNull final QuickFix[] fixes) {
    myHighlightDisplayLevel = highlightDisplayLevel;
    myPropertyName = propertyName;
    myDescription = description;
    myFixes = fixes;
  }

  public String getPropertyName() {
    return myPropertyName;
  }

  public HighlightDisplayLevel getHighlightDisplayLevel() {
    return myHighlightDisplayLevel;
  }
}
