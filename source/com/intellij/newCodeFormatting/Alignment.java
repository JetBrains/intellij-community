package com.intellij.newCodeFormatting;

public class Alignment {
  private int myCurrentOffset = -1;

  public static class Type{
    public static final Type FULL = new Type();
    public static final Type NORMAL = new Type();
  }

  private final Type myType;

  public Alignment(final Type type) {
    myType = type;
  }

  Type getType() {
    return myType;
  }

  int getCurrentOffset() {
    return myCurrentOffset;
  }

  void setCurrentOffset(final int currentIndent) {
    myCurrentOffset = currentIndent;
  }

}
