package com.intellij.newCodeFormatting;

public class Alignment {
  private int myCurrentOffset = -1;

  public static class Type{
    public static final Type FULL = new Type();
    public static final Type NORMAL = new Type();
  }

  private final Type myType;

  private int myLastLine = -1;

  public Alignment(final Type type) {
    myType = type;
  }

  public Type getType() {
    return myType;
  }

  public int getCurrentOffset() {
    return myCurrentOffset;
  }

  public void setCurrentOffset(final int currentIndent, final int lineNumber) {
    if (myLastLine != lineNumber) {
      myCurrentOffset = currentIndent;
    }
    myLastLine = lineNumber;
  }

}
