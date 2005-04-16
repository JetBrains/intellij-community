package com.intellij.newCodeFormatting;

import com.intellij.openapi.util.TextRange;
import com.intellij.util.IncorrectOperationException;

public interface FormattingModel {
  int getLineNumber(int offset);
  int getLineStartOffset(int line);

  void replaceWhiteSpace(TextRange textRange, String whiteSpace) throws IncorrectOperationException;

  CharSequence getText(final TextRange textRange);

  void runModificationTransaction(Runnable action) throws IncorrectOperationException;

  int getTextLength();
}
