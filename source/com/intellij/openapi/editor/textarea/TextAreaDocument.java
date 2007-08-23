package com.intellij.openapi.editor.textarea;

import javax.swing.*;
import javax.swing.text.BadLocationException;

/**
 * @author yole
 */
public class TextAreaDocument extends TextComponentDocument {
  private JTextArea myTextArea;

  public TextAreaDocument(final JTextArea textComponent) {
    super(textComponent);
    myTextArea = textComponent;
  }

  public int getLineCount() {
    return myTextArea.getLineCount();
  }

  public int getLineNumber(final int offset) {
    try {
      return myTextArea.getLineOfOffset(offset);
    }
    catch (BadLocationException e) {
      throw new RuntimeException(e);
    }
  }

  public int getLineStartOffset(final int line) {
    try {
      return myTextArea.getLineStartOffset(line);
    }
    catch (BadLocationException e) {
      throw new RuntimeException(e);
    }
  }

  public int getLineEndOffset(final int line) {
    try {
      return myTextArea.getLineEndOffset(line) - getLineSeparatorLength(line);
    }
    catch (BadLocationException e) {
      throw new RuntimeException(e);
    }
  }

  public int getLineSeparatorLength(final int line) {
    if (line == myTextArea.getLineCount()-1) {
      return 0;
    }
    try {
      int endOffset = myTextArea.getLineEndOffset(line) - 1;
      int startOffset = myTextArea.getLineStartOffset(line);
      int l = 0;
      String text = myTextArea.getText();
      while(l < endOffset - startOffset && (text.charAt(endOffset - l) == '\r' || text.charAt(endOffset - l) == '\n')) {
        l++;
      }
      return l;
    }
    catch (BadLocationException e) {
      throw new RuntimeException(e);
    }
  }
}