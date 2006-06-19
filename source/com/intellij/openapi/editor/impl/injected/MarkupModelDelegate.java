package com.intellij.openapi.editor.impl.injected;

import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.editor.impl.HighlighterList;
import com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
*/
class MarkupModelDelegate extends UserDataHolderBase implements MarkupModelEx {
  private final DocumentRange myDocument;
  private final MarkupModelEx myHostModel;

  public MarkupModelDelegate(MarkupModelEx editorMarkupModel, final DocumentRange document) {
    myDocument = document;
    myHostModel = editorMarkupModel;
  }

  @NotNull
  public DocumentRange getDocument() {
    return myDocument;
  }

  @NotNull
  public RangeHighlighter addRangeHighlighter(final int startOffset,
                                              final int endOffset,
                                              final int layer,
                                              final TextAttributes textAttributes,
                                              final HighlighterTargetArea targetArea) {
    return myHostModel.addRangeHighlighter(myDocument.injectedToHost(startOffset), myDocument.injectedToHost(endOffset), layer,
                                           textAttributes, targetArea);
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
}
