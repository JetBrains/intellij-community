package com.intellij.uiDesigner;

import com.intellij.uiDesigner.quickFixes.QuickFix;
import org.jetbrains.annotations.NotNull;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ErrorInfo {
  public final String myDescription;
  public final QuickFix[] myFixes;

  public ErrorInfo(@NotNull final String description, @NotNull final QuickFix[] fixes) {
    if (description == null) {
      //noinspection HardCodedStringLiteral
      throw new IllegalArgumentException("description cannot be null");
    }
    if (fixes == null) {
      //noinspection HardCodedStringLiteral
      throw new IllegalArgumentException("fixes cannot be null");
    }
    myDescription = description;
    myFixes = fixes;
  }
}
