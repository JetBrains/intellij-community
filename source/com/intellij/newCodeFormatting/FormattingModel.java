package com.intellij.newCodeFormatting;

import com.intellij.openapi.util.TextRange;

public interface FormattingModel {
  int getLineNumber(int offset);
  int getLineStartOffset(int line);
  int getLineEndOffset(int line);

  void replaceWhiteSpace(TextRange textRange, String whiteSpace);

  CharSequence getText(final TextRange textRange);
}
