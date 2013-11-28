package org.jetbrains.postfixCompletion.infrastructure;

import org.jetbrains.annotations.NotNull;

public final class PostfixExecutionContext {
  public final boolean isForceMode;
  @NotNull public final String dummyIdentifier;
  public final boolean insideCodeFragment;

  public PostfixExecutionContext(
    boolean isForceMode, @NotNull String dummyIdentifier, boolean insideCodeFragment) {
    this.isForceMode = isForceMode;
    this.dummyIdentifier = dummyIdentifier;
    this.insideCodeFragment = insideCodeFragment;
  }
}
