package com.intellij.codeInsight.template.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.impl.RangeMarkerImpl;

import java.util.ArrayList;

public class TemplateSegments {
  private ArrayList<RangeMarker> mySegments = new ArrayList<RangeMarker>();
  private Editor myEditor;

  public TemplateSegments(Editor editor) {
    myEditor = editor;
  }

  public int getSegmentStart(int i) {
    RangeMarker rangeMarker = mySegments.get(i);
    return rangeMarker.getStartOffset();
  }

  public int getSegmentEnd(int i) {
    RangeMarker rangeMarker = mySegments.get(i);
    return rangeMarker.getEndOffset();
  }

  public boolean isValid(int i) {
    return mySegments.get(i).isValid();
  }

  public void removeAll() {
    mySegments.clear();
  }

  public void addSegment(int start, int end) {
    RangeMarker rangeMarker = (myEditor.getDocument()).createRangeMarker(start, end);
    mySegments.add(rangeMarker);
  }

  public void setSegmentGreedy (int i, boolean state) {
    RangeMarker marker = mySegments.get(i);
    marker.setGreedyToLeft(state);
    marker.setGreedyToRight(state);
  }

  public boolean isInvalid() {
    for (int i = 0; i < mySegments.size(); i++) {
      RangeMarker marker = mySegments.get(i);
      if(!marker.isValid()) {
        return true;
      }
    }
    return false;
  }

  public void replaceSegmentAt(int index, int start, int end) {
    RangeMarker rangeMarker = mySegments.get(index);
    ((RangeMarkerImpl)rangeMarker).invalidate();
    Document doc = myEditor.getDocument();
    rangeMarker = doc.createRangeMarker(start, end);
    mySegments.set(index, rangeMarker);
  }
}