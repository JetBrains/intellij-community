package org.jetbrains.postfixCompletion.Infrastructure;

import org.jetbrains.annotations.*;

public final class PostfixExecutionContext {
  public final boolean isForceMode;
  @NotNull public final String dummyIdentifier;

  public PostfixExecutionContext(boolean isForceMode, @NotNull String dummyIdentifier) {
    this.isForceMode = isForceMode;
    this.dummyIdentifier = dummyIdentifier;
  }
}
