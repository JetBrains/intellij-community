package com.intellij.newCodeFormatting.impl;

import com.intellij.newCodeFormatting.Indent;

class IndentImpl implements Indent {

  static class Type{
    private final String myName;

    public Type(final String name) {
      myName = name;
    }

    public static final Type NONE = new Type("NONE");
    public static final Type LABEL = new Type("LABEL");
    public static final Type NORMAL = new Type("NORMAL");
    public static final Type START_OF_LINE = new Type("START");

    public String toString() {
      return myName;
    }
  }

  private final Type myType;
  private final int myCount;
  private final int mySpaces;

  public IndentImpl(final Type type, final int count, final int spaces) {
    myType = type;
    myCount = count;
    mySpaces = spaces;
  }

  Type getType() {
    return myType;
  }

  int getCount() {
    return myCount;
  }

  int getSpaces() {
    return mySpaces;
  }
}
