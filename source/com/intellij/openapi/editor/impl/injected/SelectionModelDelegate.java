package com.intellij.openapi.editor.impl.injected;

import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.editor.ex.EditorEx;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public class SelectionModelDelegate implements SelectionModel {
  private final SelectionModel myHostModel;
  private final DocumentRange myDocument;
  private final EditorDelegate myInjectedEditor;

  public SelectionModelDelegate(final EditorEx delegate, final DocumentRange document, EditorDelegate injectedEditor) {
    myDocument = document;
    myInjectedEditor = injectedEditor;
    myHostModel = delegate.getSelectionModel();
  }

  public int getSelectionStart() {
    return myDocument.hostToInjected(myHostModel.getSelectionStart());
  }

  public int getSelectionEnd() {
    return myDocument.hostToInjected(myHostModel.getSelectionEnd());
  }

  public String getSelectedText() {
    return myHostModel.getSelectedText();
  }

  public int getLeadSelectionOffset() {
    return myDocument.hostToInjected(myHostModel.getLeadSelectionOffset());
  }

  public boolean hasSelection() {
    return myHostModel.hasSelection();
  }

  public void setSelection(final int startOffset, final int endOffset) {
    myHostModel.setSelection(myDocument.injectedToHost(startOffset), myDocument.injectedToHost(endOffset));
  }

  public void removeSelection() {
    myHostModel.removeSelection();
  }

  public void addSelectionListener(final SelectionListener listener) {
    myHostModel.addSelectionListener(listener);
  }

  public void removeSelectionListener(final SelectionListener listener) {
    myHostModel.removeSelectionListener(listener);
  }

  public void selectLineAtCaret() {
    myHostModel.selectLineAtCaret();
  }

  public void selectWordAtCaret(final boolean honorCamelWordsSettings) {
    myHostModel.selectWordAtCaret(honorCamelWordsSettings);
  }

  public void copySelectionToClipboard() {
    myHostModel.copySelectionToClipboard();
  }

  public void setBlockSelection(final LogicalPosition blockStart, final LogicalPosition blockEnd) {
    myHostModel.setBlockSelection(myInjectedEditor.injectedToParent(blockStart), myInjectedEditor.injectedToParent(blockEnd));
  }

  public void removeBlockSelection() {
    myHostModel.removeBlockSelection();
  }

  public boolean hasBlockSelection() {
    return myHostModel.hasBlockSelection();
  }

  @NotNull
  public int[] getBlockSelectionStarts() {
    int[] result = myHostModel.getBlockSelectionStarts();
    for (int i = 0; i < result.length; i++) {
      result[i] = myDocument.hostToInjected(result[i]);
    }
    return result;
  }

  @NotNull
  public int[] getBlockSelectionEnds() {
    int[] result = myHostModel.getBlockSelectionEnds();
    for (int i = 0; i < result.length; i++) {
      result[i] = myDocument.hostToInjected(result[i]);
    }
    return result;
  }

  public LogicalPosition getBlockStart() {
    return myInjectedEditor.parentToInjected(myHostModel.getBlockStart());
  }

  public LogicalPosition getBlockEnd() {
    return myInjectedEditor.parentToInjected(myHostModel.getBlockEnd());
  }

  public boolean isBlockSelectionGuarded() {
    return myHostModel.isBlockSelectionGuarded();
  }

  public RangeMarker getBlockSelectionGuard() {
    return myHostModel.getBlockSelectionGuard();
  }
}
