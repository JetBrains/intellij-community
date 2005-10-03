package com.intellij.formatting;

import org.jetbrains.annotations.NonNls;

class IndentImpl extends Indent {
  private final boolean myIsAbsolute;

  public boolean isContinuation() {
    return myType == Type.CONTINUATION_WITHOUT_FIRST;
  }

  public boolean isNone() {
    return getType() == Type.NONE;
  }

  static class Type{
    private final String myName;


    public Type(@NonNls final String name) {
      myName = name;
    }

    public static final Type SPACES = new Type("SPACES");
    public static final Type NONE = new Type("NONE");
    public static final Type LABEL = new Type("LABEL");
    public static final Type NORMAL = new Type("NORMAL");
    public static final Type CONTINUATION = new Type("CONTINUATION");
    public static final Type CONTINUATION_WITHOUT_FIRST = new Type("CONTINUATION_WITHOUT_FIRST");

    public String toString() {
      return myName;
    }
  }

  private final Type myType;
  private final int mySpaces;

  public IndentImpl(final Type type, boolean absolute, final int spaces) {
    myType = type;
    myIsAbsolute = absolute;
    mySpaces = spaces;
  }

  public IndentImpl(final Type type, boolean absolute) {
    this(type, absolute, 0);
  }

  Type getType() {
    return myType;
  }

  public int getSpaces() {
    return mySpaces;
  }

  boolean isAbsolute(){
    return myIsAbsolute;
  }
}
