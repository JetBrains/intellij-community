package com.intellij.openapi.editor.textarea;

import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.event.SelectionListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.text.JTextComponent;

/**
 * @author yole
 */
public class TextComponentSelectionModel implements SelectionModel {
  private JTextComponent myTextComponent;

  public TextComponentSelectionModel(final JTextComponent textComponent) {
    myTextComponent = textComponent;
  }

  public int getSelectionStart() {
    return myTextComponent.getSelectionStart();
  }

  public int getSelectionEnd() {
    return myTextComponent.getSelectionEnd();
  }

  @Nullable
  public String getSelectedText() {
    return myTextComponent.getSelectedText();
  }

  public int getLeadSelectionOffset() {
    final int caretPosition = myTextComponent.getCaretPosition();
    final int start = myTextComponent.getSelectionStart();
    final int end = myTextComponent.getSelectionEnd();
    return caretPosition == start ? end : start;
  }

  public boolean hasSelection() {
    return myTextComponent.getSelectionStart() != myTextComponent.getSelectionEnd();
  }

  public void setSelection(final int startOffset, final int endOffset) {
    myTextComponent.setCaretPosition(startOffset);
    myTextComponent.moveCaretPosition(endOffset);
  }

  public void removeSelection() {
    final int position = myTextComponent.getCaretPosition();
    myTextComponent.select(position, position);
  }

  public void addSelectionListener(final SelectionListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void removeSelectionListener(final SelectionListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void selectLineAtCaret() {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void selectWordAtCaret(final boolean honorCamelWordsSettings) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void copySelectionToClipboard() {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void setBlockSelection(final LogicalPosition blockStart, final LogicalPosition blockEnd) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void removeBlockSelection() {
  }

  public boolean hasBlockSelection() {
    return false;
  }

  @NotNull
  public int[] getBlockSelectionStarts() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @NotNull
  public int[] getBlockSelectionEnds() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Nullable
  public LogicalPosition getBlockStart() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Nullable
  public LogicalPosition getBlockEnd() {
    throw new UnsupportedOperationException("Not implemented");
  }

  public boolean isBlockSelectionGuarded() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Nullable
  public RangeMarker getBlockSelectionGuard() {
    throw new UnsupportedOperationException("Not implemented");
  }
}