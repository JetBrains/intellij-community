package com.intellij.openapi.editor.ex;

/**
 *
 */
public interface LineIterator {
  void start(int startOffset);
  int getStart();
  int getEnd();
  int getSeparatorLength();
  int getLineNumber();
  void advance();
  boolean atEnd();
}
