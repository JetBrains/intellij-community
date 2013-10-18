package com.jetbrains.python.psi.types.functionalParser;

import org.jetbrains.annotations.NotNull;

/**
* @author vlan
*/
public class ParserException extends Exception {
  @NotNull private final FunctionalParserBase.State myState;

  public ParserException(@NotNull String message, @NotNull FunctionalParserBase.State state) {
    super(message);
    myState = state;
  }

  @NotNull
  FunctionalParserBase.State getState() {
    return myState;
  }
}
