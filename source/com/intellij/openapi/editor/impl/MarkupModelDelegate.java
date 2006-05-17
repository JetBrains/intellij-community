package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.UserDataHolderBase;

/**
 * @author cdr
*/
class MarkupModelDelegate extends UserDataHolderBase implements MarkupModelEx {
  private final EditorImpl myDelegate;
  private final DocumentRange myDocument;
  private final MarkupModelEx myDelegateMarkupModel;

  public MarkupModelDelegate(final EditorImpl delegate, final DocumentRange document) {

    myDelegate = delegate;
    myDocument = document;
    myDelegateMarkupModel = (MarkupModelEx)myDelegate.getMarkupModel();
  }

  public Document getDocument() {
    return myDocument;
  }

  public RangeHighlighter addRangeHighlighter(final int startOffset, final int endOffset, final int layer, final TextAttributes textAttributes,
                                              final HighlighterTargetArea targetArea) {
    return myDelegateMarkupModel.addRangeHighlighter(startOffset+myDocument.getTextRange().getStartOffset(), endOffset+myDocument.getTextRange().getStartOffset(), layer, textAttributes, targetArea);
  }

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
}
