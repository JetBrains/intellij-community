package org.jetbrains.plugins.textmate.language.preferences;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final public class IndentationRules {
  @Nullable private final String myIncreaseIndentPattern;
  @Nullable private final String myDecreaseIndentPattern;
  @Nullable private final String myIndentNextLinePattern;
  @Nullable private final String myUnIndentedLinePattern;

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

  @NotNull
  public static IndentationRules empty() {
    return new IndentationRules(null, null, null, null);
  }

  @NotNull
  public Boolean isEmpty() {
    return myIncreaseIndentPattern == null && myDecreaseIndentPattern == null
      && myIndentNextLinePattern == null && myUnIndentedLinePattern == null;
  }

  @NotNull
  public IndentationRules updateWith(IndentationRules other) {
    return new IndentationRules(
      other.myIncreaseIndentPattern != null ? other.myIncreaseIndentPattern : myIncreaseIndentPattern,
      other.myDecreaseIndentPattern != null ? other.myDecreaseIndentPattern : myDecreaseIndentPattern,
      other.myIndentNextLinePattern != null ? other.myIndentNextLinePattern : myIndentNextLinePattern,
      other.myUnIndentedLinePattern != null ? other.myUnIndentedLinePattern : myUnIndentedLinePattern
    );
  }
}