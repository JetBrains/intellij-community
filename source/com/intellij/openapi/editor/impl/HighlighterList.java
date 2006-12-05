package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import gnu.trove.THashSet;

import java.util.*;

public class HighlighterList {
  private final ArrayList<RangeHighlighterImpl> mySegmentHighlighters = new ArrayList<RangeHighlighterImpl>();
  private final Set<RangeHighlighterImpl> myHighlightersSet = new THashSet<RangeHighlighterImpl>();
  private boolean myIsDirtied = false;
  private final DocumentAdapter myDocumentListener;
  private final Document myDoc;
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
    mySegmentHighlighters.clear();
    Iterator<RangeHighlighterImpl> iterator = myHighlightersSet.iterator();
    while (iterator.hasNext()) {
      RangeHighlighterImpl rangeHighlighter = iterator.next();
      if (rangeHighlighter.isValid()) {
        mySegmentHighlighters.add(rangeHighlighter);
        myLongestHighlighterLength = Math.max(myLongestHighlighterLength, rangeHighlighter.getEndOffset() - rangeHighlighter.getStartOffset());
      }
      else {
        iterator.remove();
      }
    }
    Collections.sort(mySegmentHighlighters, new Comparator<RangeHighlighterImpl>() {
      public int compare(RangeHighlighterImpl r1, RangeHighlighterImpl r2) {
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

  public Iterator<RangeHighlighterImpl> getHighlighterIterator() {
    if (myIsDirtied) sortMarkers();
    return mySegmentHighlighters.iterator();
  }

  ArrayList<RangeHighlighterImpl> getSortedHighlighters() {
    if (myIsDirtied) sortMarkers();
    return mySegmentHighlighters;
  }

  public void addSegmentHighlighter(RangeHighlighter segmentHighlighter) {
    myIsDirtied = true;
    myHighlightersSet.add((RangeHighlighterImpl)segmentHighlighter);
  }

  public void removeSegmentHighlighter(RangeHighlighter segmentHighlighter) {
    myIsDirtied = true;
    myHighlightersSet.remove(segmentHighlighter);
  }
}
