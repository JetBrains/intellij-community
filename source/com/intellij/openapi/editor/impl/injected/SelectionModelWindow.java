package com.intellij.openapi.editor.impl.injected;

import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public class SelectionModelWindow implements SelectionModel {
  private final SelectionModel myHostModel;
  private final DocumentWindow myDocument;
  private final EditorWindow myInjectedEditor;

  public SelectionModelWindow(final EditorEx delegate, final DocumentWindow document, EditorWindow injectedEditor) {
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
    TextRange hostRange = myDocument.injectedToHost(new TextRange(startOffset, endOffset));
    myHostModel.setSelection(hostRange.getStartOffset(), hostRange.getEndOffset());
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
    myHostModel.setBlockSelection(myInjectedEditor.injectedToHost(blockStart), myInjectedEditor.injectedToHost(blockEnd));
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
    return myInjectedEditor.hostToInjected(myHostModel.getBlockStart());
  }

  public LogicalPosition getBlockEnd() {
    return myInjectedEditor.hostToInjected(myHostModel.getBlockEnd());
  }

  public boolean isBlockSelectionGuarded() {
    return myHostModel.isBlockSelectionGuarded();
  }

  public RangeMarker getBlockSelectionGuard() {
    return myHostModel.getBlockSelectionGuard();
  }

}