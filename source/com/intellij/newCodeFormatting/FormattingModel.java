package com.intellij.newCodeFormatting;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.project.Project;
import com.intellij.util.IncorrectOperationException;

public interface FormattingModel {
  int getLineNumber(int offset);
  int getLineStartOffset(int line);

  /**
   * 
   * @return new text range for block after the white space
   * @throws IncorrectOperationException
   * @param textRange
   * @param whiteSpace
   * @param oldBlockTextRange
   * @param blockIsWritable
   */ 
  TextRange replaceWhiteSpace(TextRange textRange, String whiteSpace, final TextRange oldBlockTextRange, final boolean blockIsWritable) throws IncorrectOperationException;

  CharSequence getText(final TextRange textRange);

  void runModificationTransaction(Runnable action) throws IncorrectOperationException;

  int getTextLength();
  
  void commitChanges() throws IncorrectOperationException;

  Project getProject();
}
