/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Apr 22, 2002
 * Time: 5:51:22 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.FoldRegion;
import org.jetbrains.annotations.NotNull;

public class FoldRegionImpl extends RangeMarkerImpl implements FoldRegion {
  private boolean myIsExpanded;
  private EditorImpl myEditor;
  private String myPlaceholderText;

  public FoldRegionImpl(EditorImpl editor, int startOffset, int endOffset, String placeholder) {
    super(editor.getDocument(), startOffset, endOffset);
    myIsExpanded = true;
    myEditor = editor;
    myPlaceholderText = placeholder;
  }

  public boolean isExpanded() {
    return myIsExpanded;
  }

  public void setExpanded(boolean expanded) {
    FoldingModelImpl foldingModel = (FoldingModelImpl)myEditor.getFoldingModel();
    if (expanded){
      foldingModel.expandFoldRegion(this);
    }
    else{
      foldingModel.collapseFoldRegion(this);
    }
  }

  public boolean isValid() {
    return super.isValid() && getStartOffset() + 1 < getEndOffset();
  }

  public void setExpandedInternal(boolean toExpand) {
    myIsExpanded = toExpand;
  }

  @NotNull
  public String getPlaceholderText() {
    return myPlaceholderText;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "FoldRegion (" + getStartOffset() + ":" + getEndOffset() + ")";
  }
}
