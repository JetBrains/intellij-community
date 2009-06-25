package com.intellij.spellchecker;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.Nullable;

public class CheckArea {

  private String text;
  private TextRange textRange;
  private boolean ignored;

  public CheckArea(String text, TextRange range) {
    this.text = text;
    textRange = range;
  }

  public CheckArea(String text, TextRange textRange, boolean ignored) {
    this.text = text;
    this.textRange = textRange;
    this.ignored = ignored;
  }

  public TextRange getTextRange() {
    return textRange;
  }

  public void setTextRange(TextRange textRange) {
    this.textRange = textRange;
  }

  public boolean isIgnored() {
    return ignored;
  }

  public void setIgnored(boolean ignored) {
    this.ignored = ignored;
  }

  @Nullable
  public String getWord() {
    if (text == null || textRange == null) return null;
    return text.substring(textRange.getStartOffset(), textRange.getEndOffset());
  }

  @Override
  public String toString() {
    return "CheckArea{range = " + textRange + ", ignored=" + ignored + ", word=" + (getWord()!=null?getWord():"") +'}';
  }
}
