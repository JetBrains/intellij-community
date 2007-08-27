package com.intellij.openapi.editor.impl.injected;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.impl.HighlighterList;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
*/
class MarkupModelWindow extends UserDataHolderBase implements MarkupModelEx {
  private final DocumentWindow myDocument;
  private final MarkupModelEx myHostModel;

  public MarkupModelWindow(MarkupModelEx editorMarkupModel, final DocumentWindow document) {
    myDocument = document;
    myHostModel = editorMarkupModel;
  }

  @NotNull
  public Document getDocument() {
    return myDocument;
  }

  @NotNull
  public RangeHighlighter addRangeHighlighter(final int startOffset,
                                              final int endOffset,
                                              final int layer,
                                              final TextAttributes textAttributes,
                                              final HighlighterTargetArea targetArea) {
    TextRange hostRange = myDocument.injectedToHost(new TextRange(startOffset, endOffset));
    return myHostModel.addRangeHighlighter(hostRange.getStartOffset(), hostRange.getEndOffset(), layer, textAttributes, targetArea);
  }

  @NotNull
  public RangeHighlighter addLineHighlighter(final int line, final int layer, final TextAttributes textAttributes) {
    int hostLine = myDocument.injectedToHostLine(line);
    return myHostModel.addLineHighlighter(hostLine, layer, textAttributes);
  }

  public void removeHighlighter(final RangeHighlighter rangeHighlighter) {
    myHostModel.removeHighlighter(rangeHighlighter);
  }

  public void removeAllHighlighters() {
    myHostModel.removeAllHighlighters();
  }

  @NotNull
  public RangeHighlighter[] getAllHighlighters() {
    return myHostModel.getAllHighlighters();
  }

  public void dispose() {
    myHostModel.dispose();
  }

  public HighlighterList getHighlighterList() {
    return myHostModel.getHighlighterList();
  }

  public RangeHighlighter addPersistentLineHighlighter(final int line, final int layer, final TextAttributes textAttributes) {
    int hostLine = myDocument.injectedToHostLine(line);
    return myHostModel.addPersistentLineHighlighter(hostLine, layer, textAttributes);
  }


  public boolean containsHighlighter(final RangeHighlighter highlighter) {
    return myHostModel.containsHighlighter(highlighter);
  }

  public void addMarkupModelListener(MarkupModelListener listener) {
    myHostModel.addMarkupModelListener(listener);
  }


  public void removeMarkupModelListener(MarkupModelListener listener) {
    myHostModel.removeMarkupModelListener(listener);
  }

  public void setRangeHighlighterAttributes(final RangeHighlighter highlighter, final TextAttributes textAttributes) {
    myHostModel.setRangeHighlighterAttributes(highlighter, textAttributes);
  }

}