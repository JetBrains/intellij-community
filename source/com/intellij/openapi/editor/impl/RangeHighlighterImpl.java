package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Implementation of the markup element for the editor and document.
 * @author max
 */
public class RangeHighlighterImpl implements RangeHighlighterEx {
  private final MarkupModel myModel;
  private final int myLayer;
  private final HighlighterTargetArea myTargetArea;
  private TextAttributes myTextAttributes;
  private LineMarkerRenderer myLineMarkerRenderer;
  private Color myErrorStripeColor;
  private Color myLineSeparatorColor;
  private SeparatorPlacement mySeparatorPlacement;
  private boolean isAfterEndOfLine;
  private final RangeMarkerImpl myRangeMarker;
  private GutterIconRenderer myGutterIconRenderer;
  private boolean myErrorStripeMarkIsThin;
  private Object myErrorStripeTooltip;
  private MarkupEditorFilter myFilter = MarkupEditorFilter.EMPTY;

  RangeHighlighterImpl(MarkupModel model,
                       int start,
                       int end,
                       int layer,
                       HighlighterTargetArea target,
                       TextAttributes textAttributes,
                       boolean persistent) {
    myRangeMarker = persistent
                    ? new PersistentLineMarker(model.getDocument(), start)
                    : new RangeMarkerImpl(model.getDocument(), start, end);
    myTextAttributes = textAttributes;
    myTargetArea = target;
    myLayer = layer;
    myModel = model;
    if (textAttributes != null) {
      myErrorStripeColor = textAttributes.getErrorStripeColor();
    }
  }

  public TextAttributes getTextAttributes() {
    return myTextAttributes;
  }

  public void setTextAttributes(final TextAttributes textAttributes) {
    myTextAttributes = textAttributes;
  }

  public int getLayer() {
    return myLayer;
  }

  public HighlighterTargetArea getTargetArea() {
    return myTargetArea;
  }

  public int getAffectedAreaStartOffset() {
    int startOffset = getStartOffset();
    if (myTargetArea == HighlighterTargetArea.EXACT_RANGE) return startOffset;
    if (startOffset == getDocument().getTextLength()) return startOffset;
    return getDocument().getLineStartOffset(getDocument().getLineNumber(startOffset));
  }

  public int getAffectedAreaEndOffset() {
    int endOffset = getEndOffset();
    if (myTargetArea == HighlighterTargetArea.EXACT_RANGE) return endOffset;
    int textLength = getDocument().getTextLength();
    if (endOffset == textLength) return endOffset;
    return Math.min(textLength, getDocument().getLineEndOffset(getDocument().getLineNumber(endOffset)) + 1);
  }

  public LineMarkerRenderer getLineMarkerRenderer() {
    return myLineMarkerRenderer;
  }

  public void setLineMarkerRenderer(LineMarkerRenderer renderer) {
    myLineMarkerRenderer = renderer;
    fireChanged();
  }

  public GutterIconRenderer getGutterIconRenderer() {
    return myGutterIconRenderer;
  }

  public void setGutterIconRenderer(GutterIconRenderer renderer) {
    myGutterIconRenderer = renderer;
    fireChanged();
  }

  public Color getErrorStripeMarkColor() {
    return myErrorStripeColor;
  }

  public void setErrorStripeMarkColor(Color color) {
    myErrorStripeColor = color;
    fireChanged();
  }

  public Object getErrorStripeTooltip() {
    return myErrorStripeTooltip;
  }

  public void setErrorStripeTooltip(Object tooltipObject) {
    myErrorStripeTooltip = tooltipObject;
  }

  public boolean isThinErrorStripeMark() {
    return myErrorStripeMarkIsThin;
  }

  public void setThinErrorStripeMark(boolean value) {
    myErrorStripeMarkIsThin = value;
  }

  public Color getLineSeparatorColor() {
    return myLineSeparatorColor;
  }

  public void setLineSeparatorColor(Color color) {
    myLineSeparatorColor = color;
    fireChanged();
  }

  public SeparatorPlacement getLineSeparatorPlacement() {
    return mySeparatorPlacement;
  }

  public void setLineSeparatorPlacement(SeparatorPlacement placement) {
    mySeparatorPlacement = placement;
    fireChanged();
  }

  public void setEditorFilter(@NotNull MarkupEditorFilter filter) {
    myFilter = filter;
  }

  @NotNull
  public MarkupEditorFilter getEditorFilter() {
    return myFilter;
  }

  public boolean isAfterEndOfLine() {
    return isAfterEndOfLine;
  }

  public void setAfterEndOfLine(boolean afterEndOfLine) {
    isAfterEndOfLine = afterEndOfLine;
  }

  private void fireChanged() {
    if (myModel instanceof MarkupModelImpl) {
      ((MarkupModelImpl)myModel).fireSegmentHighlighterChanged(this);
    }
  }

  public int getStartOffset() {
    return myRangeMarker.getStartOffset();
  }

  public long getId() {
    return myRangeMarker.getId();
  }

  public int getEndOffset() {
    return myRangeMarker.getEndOffset();
  }

  public boolean isValid() {
    return myRangeMarker.isValid();
  }

  @NotNull
  public Document getDocument() {
    return myRangeMarker.getDocument();
  }

  public void setGreedyToLeft(boolean greedy) {
    myRangeMarker.setGreedyToLeft(greedy);
  }

  public void setGreedyToRight(boolean greedy) {
    myRangeMarker.setGreedyToRight(greedy);
  }

  public <T> T getUserData(Key<T> key) {
    return myRangeMarker.getUserData(key);
  }

  public <T> void putUserData(Key<T> key, T value) {
    myRangeMarker.putUserData(key, value);
  }

  public String toString() {
    return myRangeMarker.toString();
  }
}
