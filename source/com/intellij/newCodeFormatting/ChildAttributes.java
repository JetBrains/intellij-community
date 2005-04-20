package com.intellij.newCodeFormatting;

public class ChildAttributes {
  private final Indent myChildIndent;
  private final Alignment myAlignment;

  public ChildAttributes(final Indent childIndent, final Alignment alignment) {
    myChildIndent = childIndent;
    myAlignment = alignment;
  }

  public Indent getChildIndent() {
    return myChildIndent;
  }

  public Alignment getAlignment() {
    return myAlignment;
  }
}
