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
  private final TextComponentEditor myEditor;

  public TextComponentCaretModel(final JTextComponent textComponent, TextComponentEditor editor) {
    myTextComponent = textComponent;
    myEditor = editor;
  }

  public void moveCaretRelatively(final int columnShift,
                                  final int lineShift,
                                  final boolean withSelection, final boolean blockSelection, final boolean scrollToCaret) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void moveToLogicalPosition(final LogicalPosition pos) {
    moveToOffset(myEditor.logicalPositionToOffset(pos));
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
    LogicalPosition pos = getLogicalPosition();
    return new VisualPosition(pos.line, pos.column);
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