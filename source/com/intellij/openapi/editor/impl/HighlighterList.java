package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.markup.RangeHighlighter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;

public class HighlighterList {
  private ArrayList<RangeHighlighterImpl> mySegmentHighlighters = new ArrayList<RangeHighlighterImpl>();
  private boolean myIsDirtied = false;
  private DocumentAdapter myDocumentListener;
  private Document myDoc;
  private int myLongestHighlighterLength = 0;

  public HighlighterList(Document doc) {
    myDocumentListener = new DocumentAdapter() {
      public void documentChanged(DocumentEvent e) {
        myIsDirtied = true;
      }
    };
    myDoc = doc;
    myDoc.addDocumentListener(myDocumentListener);
  }

  public void dispose() {
    myDoc.removeDocumentListener(myDocumentListener);
  }

  public int getLongestHighlighterLength() {
    return myLongestHighlighterLength;
  }

  private void sortMarkers() {
    myLongestHighlighterLength = 0;
    RangeHighlighterImpl[] segmentHighlighters = mySegmentHighlighters.toArray(new RangeHighlighterImpl[mySegmentHighlighters.size()]);
    for (int i = 0; i < segmentHighlighters.length; i++) {
      RangeHighlighterImpl segmentHighlighter = segmentHighlighters[i];
      if (!segmentHighlighter.isValid()) mySegmentHighlighters.remove(segmentHighlighter);
      myLongestHighlighterLength =
      Math.max(myLongestHighlighterLength, segmentHighlighter.getEndOffset() - segmentHighlighter.getStartOffset());
    }

    Collections.sort(mySegmentHighlighters, new Comparator<RangeHighlighterImpl>() {
      public int compare(RangeHighlighterImpl r1, RangeHighlighterImpl r2) {
//        RangeHighlighterImpl r1 = (RangeHighlighterImpl) o1;
//        RangeHighlighterImpl r2 = (RangeHighlighterImpl) o2;

        if (r1.getAffectedAreaStartOffset() != r2.getAffectedAreaStartOffset()) {
          return r1.getAffectedAreaStartOffset() - r2.getAffectedAreaStartOffset();
        }

        if (r1.getLayer() != r2.getLayer()) {
          return r2.getLayer() - r1.getLayer();
        }

        return (int) (r2.getId() - r1.getId());
      }
    });

    myIsDirtied = false;
  }

  public Iterator getHighlighterIterator() {
    if (myIsDirtied) sortMarkers();
    return mySegmentHighlighters.iterator();
  }

  ArrayList<RangeHighlighterImpl> getSortedHighlighters() {
    if (myIsDirtied) sortMarkers();
    return mySegmentHighlighters;
  }

  public void addSegmentHighlighter(RangeHighlighter segmentHighlighter) {
    myIsDirtied = true;
    mySegmentHighlighters.add((RangeHighlighterImpl)segmentHighlighter);
  }

  public void removeSegmentHighlighter(RangeHighlighter segmentHighlighter) {
    myIsDirtied = true;
    mySegmentHighlighters.remove(segmentHighlighter);
  }
}
