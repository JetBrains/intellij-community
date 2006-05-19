package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.ex.ErrorStripeListener;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.editor.markup.ErrorStripeRenderer;
import com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
*/
class MarkupModelDelegate extends UserDataHolderBase implements EditorMarkupModel, MarkupModelEx {
  private final DocumentRange myDocument;
  private final EditorDelegate myEditor;
  private final EditorMarkupModelImpl myDelegateMarkupModel;

  public MarkupModelDelegate(EditorMarkupModelImpl editorMarkupModel, final DocumentRange document, EditorDelegate editor) {
    myDocument = document;
    myEditor = editor;
    myDelegateMarkupModel = editorMarkupModel;
  }

  @NotNull
  public Document getDocument() {
    return myDocument;
  }

  @NotNull
  public RangeHighlighter addRangeHighlighter(final int startOffset, final int endOffset, final int layer, final TextAttributes textAttributes,
                                              final HighlighterTargetArea targetArea) {
    return myDelegateMarkupModel.addRangeHighlighter(startOffset+myDocument.getTextRange().getStartOffset(), endOffset+myDocument.getTextRange().getStartOffset(), layer, textAttributes, targetArea);
  }

  @NotNull
  public RangeHighlighter addLineHighlighter(final int line, final int layer, final TextAttributes textAttributes) {
    int offLine = myDocument.getDelegate().getLineNumber(myDocument.getTextRange().getStartOffset());
    return myDelegateMarkupModel.addLineHighlighter(line + offLine, layer, textAttributes);
  }

  public void removeHighlighter(final RangeHighlighter rangeHighlighter) {
    myDelegateMarkupModel.removeHighlighter(rangeHighlighter);
  }

  public void removeAllHighlighters() {
    myDelegateMarkupModel.removeAllHighlighters();
  }

  @NotNull
  public RangeHighlighter[] getAllHighlighters() {
    return myDelegateMarkupModel.getAllHighlighters();
  }

  public void dispose() {
    myDelegateMarkupModel.dispose();
  }

  public HighlighterList getHighlighterList() {
    return myDelegateMarkupModel.getHighlighterList();
  }

  public RangeHighlighter addPersistentLineHighlighter(final int line, final int layer, final TextAttributes textAttributes) {
    int offLine = myDocument.getDelegate().getLineNumber(myDocument.getTextRange().getStartOffset());
    return myDelegateMarkupModel.addPersistentLineHighlighter(line+offLine, layer, textAttributes);
  }


  public boolean containsHighlighter(final RangeHighlighter highlighter) {
    return myDelegateMarkupModel.containsHighlighter(highlighter);
  }

  public void setErrorStripeVisible(final boolean val) {
    myDelegateMarkupModel.setErrorStripeVisible(val);
  }

  public Editor getEditor() {
    return myEditor;
  }

  public void setErrorStripeRenderer(final ErrorStripeRenderer renderer) {
    myDelegateMarkupModel.setErrorStripeRenderer(renderer);
  }

  public ErrorStripeRenderer getErrorStripeRenderer() {
    return myDelegateMarkupModel.getErrorStripeRenderer();
  }

  public void addErrorMarkerListener(final ErrorStripeListener listener) {
    myDelegateMarkupModel.addErrorMarkerListener(listener);
  }

  public void removeErrorMarkerListener(final ErrorStripeListener listener) {
    myDelegateMarkupModel.removeErrorMarkerListener(listener);
  }

}
