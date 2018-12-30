package com.intellij.bash.lexer;

import com.intellij.lexer.FlexLexer;

public interface StatesSwitcher extends BashTokenTypes, FlexLexer {
  boolean isBash4();

  /**
   * Goes to the given state and stores the previous state on the stack of states.
   * This makes it possible to have several levels of lexing, e.g. for $(( 1+ $(echo 3) )).
   */
  void goToState(int newState);

  /**
   * Goes back to the previous state of the lexer. If there
   * is no previous state then YYINITIAL, the initial state, is chosen.
   */
  void backToPreviousState();

  void popStates(int lastStateToPop);

  boolean isInState(int state);

  int openParenthesisCount();

  void incOpenParenthesisCount();

  void decOpenParenthesisCount();

  boolean isParamExpansionHash();

  void setParamExpansionHash(boolean paramExpansionHash);

  boolean isParamExpansionWord();

  void setParamExpansionWord(boolean paramExpansionWord);

  boolean isParamExpansionOther();

  void setParamExpansionOther(boolean paramExpansionOther);

  boolean isInCaseBody();

  void setInCaseBody(boolean inCaseBody);

  StringLexingState stringParsingState();

  boolean isEmptyConditionalCommand();

  void setEmptyConditionalCommand(boolean emptyConditionalCommand);

  HeredocLexingState heredocState();

  boolean isInHereStringContent();

  void enterHereStringContent();

  void leaveHereStringContent();
}
