package com.intellij.openapi.diff.impl.processing;

import com.intellij.openapi.util.TextRange;

public class Formatting extends Word {
  public Formatting(String text, TextRange range) {
    super(text, range);
  }

  public int hashCode() {
    return -1;
  }

  public boolean equals(Object obj) {
    return obj instanceof Formatting;
  }

  public boolean isWhitespace() {
    return true;
  }
}
