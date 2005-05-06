package com.intellij.newCodeFormatting;

import com.intellij.openapi.util.TextRange;
import com.intellij.util.IncorrectOperationException;

public interface FormattingModel {
  int getLineNumber(int offset);
  int getLineStartOffset(int line);

  /**
   * 
   * @param textRange
   * @return new text range for block after the white space
   * @throws IncorrectOperationException
   * @param whiteSpace
   * @param oldBlockTextRange
   */ 
  TextRange replaceWhiteSpace(TextRange textRange, String whiteSpace, final TextRange oldBlockTextRange) throws IncorrectOperationException;

  CharSequence getText(final TextRange textRange);

  void runModificationTransaction(Runnable action) throws IncorrectOperationException;

  int getTextLength();
  
  void commitChanges();
}
