package com.intellij.newCodeFormatting;

public interface Wrap {
  class Type{
    private final String myName;

    private Type(final String name) {
      myName = name;
    }

    public static final Type DO_NOT_WRAP = new Type("NONE");
    public static final Type WRAP_AS_NEEDED = new Type("NORMAL");
    public static final Type CHOP_IF_NEEDED = new Type("CHOP");
    public static final Type WRAP_ALWAYS = new Type("ALWAYS");

    public String toString() {
      return myName;
    }
  }

}
