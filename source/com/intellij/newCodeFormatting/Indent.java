package com.intellij.newCodeFormatting;

public class Indent {

  public static class Type{
    public static final Type NONE = new Type();
    public static final Type LABEL = new Type();
    public static final Type NORMAL = new Type();
    public static final Type CONTINUATION = new Type();
  }

  private final Type myType;
  private final int myCount;
  private final int mySpaces;

  public Indent(final Type type, final int count, final int spaces) {
    myType = type;
    myCount = count;
    mySpaces = spaces;
  }

  public Type getType() {
    return myType;
  }

  public int getCount() {
    return myCount;
  }

  public int getSpaces() {
    return mySpaces;
  }
}
