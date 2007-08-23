package com.intellij.openapi.editor.textarea;

import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.event.CaretListener;

import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import javax.swing.*;

/**
 * @author yole
 */
public class TextComponentCaretModel implements CaretModel {
  private JTextComponent myTextComponent;

  public TextComponentCaretModel(final JTextComponent textComponent) {
    myTextComponent = textComponent;
  }

  public void moveCaretRelatively(final int columnShift,
                                  final int lineShift,
                                  final boolean withSelection, final boolean blockSelection, final boolean scrollToCaret) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void moveToLogicalPosition(final LogicalPosition pos) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void moveToVisualPosition(final VisualPosition pos) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void moveToOffset(final int offset) {
    myTextComponent.setCaretPosition(offset);
  }

  public LogicalPosition getLogicalPosition() {
    int caretPos = myTextComponent.getCaretPosition();
    int line;
    int lineStart;
    if (myTextComponent instanceof JTextArea) {
      final JTextArea textArea = (JTextArea)myTextComponent;
      try {
        line = textArea.getLineOfOffset(caretPos);
        lineStart = textArea.getLineStartOffset(line);
      }
      catch (BadLocationException e) {
        throw new RuntimeException(e);
      }
    }
    else {
      line = 0;
      lineStart = 0;
    }
    return new LogicalPosition(line, caretPos - lineStart);
  }

  public VisualPosition getVisualPosition() {
    throw new UnsupportedOperationException("Not implemented");
  }

  public int getOffset() {
    return myTextComponent.getCaretPosition();
  }

  public void addCaretListener(final CaretListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void removeCaretListener(final CaretListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }
}