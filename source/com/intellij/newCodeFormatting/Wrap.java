package com.intellij.newCodeFormatting;

public class Wrap {
  private int myFirstEntry = -1;
  private int myFirstPosition = -1;
  private boolean myIsActive = false;
  private final boolean myWrapFirstElement;

  int getFirstEntry() {
    return myFirstEntry;
  }

  void markAsUsed() {
    myFirstEntry = -1;
    myIsActive = true;
  }

  void processNextEntry(final int startOffset) {
    if (myFirstPosition < 0) {
      myFirstPosition = startOffset;
    }
  }

  int getFirstPosition() {
    return myFirstPosition;
  }

  public static class Type{
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

  private final Type myType;

  public Wrap(final Type type, boolean wrapFirstElement) {
    myType = type;
    myWrapFirstElement = wrapFirstElement;

  }

  Type getType() {
    return myType;
  }

  boolean isWrapFirstElement() {
    return myWrapFirstElement;
  }

  void saveFirstEntry(final int startOffset) {
    if (myFirstEntry < 0) {
      myFirstEntry = startOffset;
    }
  }

  boolean isIsActive() {
    return myIsActive;
  }

  public String toString() {
    return myType.toString();
  }
}
