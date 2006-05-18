package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.editor.ex.EditorEx;

/**
 * @author cdr
 */
public class SelectionModelDelegate implements SelectionModel {
  private final SelectionModel myDelegate;
  private final DocumentRange myDocument;
  private final EditorDelegate myInjectedEditor;

  public SelectionModelDelegate(final EditorEx delegate, final DocumentRange document, EditorDelegate injectedEditor) {
    myDocument = document;
    myInjectedEditor = injectedEditor;
    myDelegate = delegate.getSelectionModel();
  }

  public int getSelectionStart() {
    return myDelegate.getSelectionStart() - myDocument.getTextRange().getStartOffset();
  }

  public int getSelectionEnd() {
    return myDelegate.getSelectionEnd() - myDocument.getTextRange().getStartOffset();
  }

  public String getSelectedText() {
    return myDelegate.getSelectedText();
  }

  public int getLeadSelectionOffset() {
    return myDelegate.getLeadSelectionOffset() - myDocument.getTextRange().getStartOffset();
  }

  public boolean hasSelection() {
    return myDelegate.hasSelection();
  }

  public void setSelection(final int startOffset, final int endOffset) {
    myDelegate.setSelection(startOffset + myDocument.getTextRange().getStartOffset(), endOffset + myDocument.getTextRange().getStartOffset());
  }

  public void removeSelection() {
    myDelegate.removeSelection();
  }

  public void addSelectionListener(final SelectionListener listener) {
    myDelegate.addSelectionListener(listener);
  }

  public void removeSelectionListener(final SelectionListener listener) {
    myDelegate.removeSelectionListener(listener);
  }

  public void selectLineAtCaret() {
    myDelegate.selectLineAtCaret();
  }

  public void selectWordAtCaret(final boolean honorCamelWordsSettings) {
    myDelegate.selectWordAtCaret(honorCamelWordsSettings);
  }

  public void copySelectionToClipboard() {
    myDelegate.copySelectionToClipboard();
  }

  public void setBlockSelection(final LogicalPosition blockStart, final LogicalPosition blockEnd) {
    myDelegate.setBlockSelection(blockStart, blockEnd);
  }

  public void removeBlockSelection() {
    myDelegate.removeBlockSelection();
  }

  public boolean hasBlockSelection() {
    return myDelegate.hasBlockSelection();
  }

  public int[] getBlockSelectionStarts() {
    return myDelegate.getBlockSelectionStarts();
  }

  public int[] getBlockSelectionEnds() {
    return myDelegate.getBlockSelectionEnds();
  }

  public LogicalPosition getBlockStart() {
    return myInjectedEditor.parentToInjected(myDelegate.getBlockStart());
  }

  public LogicalPosition getBlockEnd() {
    return myInjectedEditor.parentToInjected(myDelegate.getBlockEnd());
  }

  public boolean isBlockSelectionGuarded() {
    return myDelegate.isBlockSelectionGuarded();
  }

  public RangeMarker getBlockSelectionGuard() {
    return myDelegate.getBlockSelectionGuard();
  }
}
