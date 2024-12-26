package org.jetbrains.plugins.textmate.language.preferences;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class IndentationRules {
  private final @Nullable String myIncreaseIndentPattern;
  private final @Nullable String myDecreaseIndentPattern;
  private final @Nullable String myIndentNextLinePattern;
  private final @Nullable String myUnIndentedLinePattern;

  public IndentationRules(@Nullable String increaseIndentPattern,
                          @Nullable String decreaseIndentPattern,
                          @Nullable String indentNextLinePattern,
                          @Nullable String unIndentedLinePattern) {
    myIncreaseIndentPattern = increaseIndentPattern;
    myDecreaseIndentPattern = decreaseIndentPattern;
    myIndentNextLinePattern = indentNextLinePattern;
    myUnIndentedLinePattern = unIndentedLinePattern;
  }

  public @Nullable String getIncreaseIndentPattern() { return myIncreaseIndentPattern; }
  public @Nullable String getDecreaseIndentPattern() { return myDecreaseIndentPattern; }
  public @Nullable String getIndentNextLinePattern() { return myIndentNextLinePattern; }
  public @Nullable String getUnIndentedLinePattern() { return myUnIndentedLinePattern; }

  public static @NotNull IndentationRules empty() {
    return new IndentationRules(null, null, null, null);
  }

  public @NotNull Boolean isEmpty() {
    return myIncreaseIndentPattern == null && myDecreaseIndentPattern == null
      && myIndentNextLinePattern == null && myUnIndentedLinePattern == null;
  }

  public @NotNull IndentationRules updateWith(IndentationRules other) {
    return new IndentationRules(
      other.myIncreaseIndentPattern != null ? other.myIncreaseIndentPattern : myIncreaseIndentPattern,
      other.myDecreaseIndentPattern != null ? other.myDecreaseIndentPattern : myDecreaseIndentPattern,
      other.myIndentNextLinePattern != null ? other.myIndentNextLinePattern : myIndentNextLinePattern,
      other.myUnIndentedLinePattern != null ? other.myUnIndentedLinePattern : myUnIndentedLinePattern
    );
  }
}