package com.intellij.newCodeFormatting;

public class Wrap {
  public static class Type{
    public static final Type DO_NOT_WRAP = new Type();
    public static final Type WRAP_AS_NEEDED = new Type();
    public static final Type CHOP_IF_NEEDED = new Type();
    public static final Type WRAP_ALWAYS = new Type();
  }

  private final Type myType;

  public Wrap(final Type type) {
    myType = type;
  }

  public Type getType() {
    return myType;
  }

  public void markAsUsed() {
  }
  
}
