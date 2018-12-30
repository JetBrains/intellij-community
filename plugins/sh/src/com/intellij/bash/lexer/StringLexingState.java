package com.intellij.bash.lexer;

import com.intellij.util.containers.Stack;

/**
 * Class to store information about the current parsing of strings.
 * In bash strings can be nested. An expression like "$("$("abcd")")" is one string which contains subshell commands.
 * Each subshell command contains a separate string. To parse this we need a stack of parsing states.
 * This is what this class does.
 */
public class StringLexingState {
  private final Stack<SubshellState> subshells = new Stack<>(5);

  public void enterString() {
    if (!subshells.isEmpty()) {
      subshells.peek().enterString();
    }
  }

  public void leaveString() {
    if (!subshells.isEmpty()) {
      subshells.peek().leaveString();
    }
  }

  public boolean isInSubstring() {
    return !subshells.isEmpty() && subshells.peek().isInString();
  }

  public boolean isSubstringAllowed() {
    return !subshells.isEmpty() && !subshells.peek().isInString();
  }

  public boolean isInSubshell() {
    return !subshells.isEmpty();
  }

  public void enterSubshell() {
    subshells.push(new SubshellState());
  }

  public void leaveSubshell() {
    assert !subshells.isEmpty();

    subshells.pop();
  }

  private static class SubshellState {
    private int inString = 0;

    boolean isInString() {
      return inString > 0;
    }

    void enterString() {
      inString++;
    }

    void leaveString() {
      assert inString > 0 : "The inString stack should not be empty";
      inString--;
    }
  }
}
