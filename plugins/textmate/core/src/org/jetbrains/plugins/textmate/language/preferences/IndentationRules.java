package org.jetbrains.plugins.textmate.language.preferences;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.regex.RegexFacade;

final public class IndentationRules {
  @Nullable private final RegexFacade myIncreaseIndentPattern;
  @Nullable private final RegexFacade myDecreaseIndentPattern;
  @Nullable private final RegexFacade myIndentNextLinePattern;
  @Nullable private final RegexFacade myUnIndentedLinePattern;

  public IndentationRules(@Nullable RegexFacade increaseIndentPattern,
                          @Nullable RegexFacade decreaseIndentPattern,
                          @Nullable RegexFacade indentNextLinePattern,
                          @Nullable RegexFacade unIndentedLinePattern) {
    myIncreaseIndentPattern = increaseIndentPattern;
    myDecreaseIndentPattern = decreaseIndentPattern;
    myIndentNextLinePattern = indentNextLinePattern;
    myUnIndentedLinePattern = unIndentedLinePattern;
  }

  public @Nullable RegexFacade getIncreaseIndentPattern() { return myIncreaseIndentPattern; }
  public @Nullable RegexFacade getDecreaseIndentPattern() { return myDecreaseIndentPattern; }
  public @Nullable RegexFacade getIndentNextLinePattern() { return myIndentNextLinePattern; }
  public @Nullable RegexFacade getUnIndentedLinePattern() { return myUnIndentedLinePattern; }

  @NotNull
  public static IndentationRules empty() {
    return new IndentationRules(null, null, null, null);
  }

  @NotNull
  public Boolean isEmpty() {
    return myIncreaseIndentPattern == null && myDecreaseIndentPattern == null
      && myIndentNextLinePattern == null && myUnIndentedLinePattern == null;
  }

  @NotNull IndentationRules updateWith(IndentationRules other) {
    return new IndentationRules(
      myIncreaseIndentPattern != null ? myIncreaseIndentPattern : other.myIncreaseIndentPattern,
      myDecreaseIndentPattern != null ? myDecreaseIndentPattern : other.myDecreaseIndentPattern,
      myIndentNextLinePattern != null ? myIndentNextLinePattern : other.myIndentNextLinePattern,
      myUnIndentedLinePattern != null ? myUnIndentedLinePattern : other.myUnIndentedLinePattern
    );
  }
}