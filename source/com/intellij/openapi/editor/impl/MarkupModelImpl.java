/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Apr 19, 2002
 * Time: 2:26:19 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.impl.event.MarkupModelEvent;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.UserDataHolderBase;

import java.util.ArrayList;

public class MarkupModelImpl extends UserDataHolderBase implements MarkupModelEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.MarkupModelImpl");
  private DocumentImpl myDocument;

  private final HighlighterList myHighlighterList;
  private ArrayList<RangeHighlighter> myHighlighters = new ArrayList<RangeHighlighter>();
  private RangeHighlighter[] myCachedHighlighters;
  private ArrayList<MarkupModelListener> myListeners = new ArrayList<MarkupModelListener>();
  private MarkupModelListener[] myCachedListeners;

  MarkupModelImpl(DocumentImpl document) {
    myDocument = document;
    myHighlighterList = new HighlighterList(document);
  }

  public void dispose() {
    myHighlighterList.dispose();
  }

  public RangeHighlighter addLineHighlighter(int lineNumber, int layer, TextAttributes textAttributes) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (lineNumber >= getDocument().getLineCount() || lineNumber < 0) return null;

    int offset = getFirstNonspaceCharOffset(getDocument(), lineNumber);

    return addRangeHighlighter(offset, offset, layer, textAttributes, HighlighterTargetArea.LINES_IN_RANGE);
  }

  public RangeHighlighter addPersistentLineHighlighter(int lineNumber, int layer, TextAttributes textAttributes) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (lineNumber >= getDocument().getLineCount() || lineNumber < 0) return null;

    int offset = getFirstNonspaceCharOffset(getDocument(), lineNumber);

    return addRangeHighlighter(offset, offset, layer, textAttributes, HighlighterTargetArea.LINES_IN_RANGE, true);
  }

  static int getFirstNonspaceCharOffset(Document doc, int lineNumber) {
    int lineStart = doc.getLineStartOffset(lineNumber);
    int lineEnd = doc.getLineEndOffset(lineNumber);
    CharSequence text = doc.getCharsSequence();
    int offset = lineStart;
    for (int i = lineStart; i < lineEnd; i++) {
      char c = text.charAt(i);
      if (c != ' ' && c != '\t') {
        offset = i;
        break;
      }
    }
    return offset;
  }

  public RangeHighlighter addLineHighliterAtOffset(int startOffset,
                                                   int endOffset,
                                                   int layer,
                                                   TextAttributes textAttributes) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    RangeHighlighterImpl highlighter = new RangeHighlighterImpl(this, startOffset, endOffset, layer,
                                                                HighlighterTargetArea.LINES_IN_RANGE, textAttributes,
                                                                false);

    myHighlighters.add(highlighter);
    myCachedHighlighters = null;
    myHighlighterList.addSegmentHighlighter(highlighter);
    fireSegmentHighlighterChanged(highlighter);
    return highlighter;
  }

  public RangeHighlighter[] getAllHighlighters() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myCachedHighlighters == null) {
      myCachedHighlighters = myHighlighters.toArray(new RangeHighlighter[myHighlighters.size()]);
    }
    return myCachedHighlighters;
  }

  private RangeHighlighter addRangeHighlighter(int startOffset,
                                              int endOffset,
                                              int layer,
                                              TextAttributes textProperties,
                                              HighlighterTargetArea targetArea,
                                              boolean isPersistent) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    RangeHighlighterImpl segmentHighlighter = new RangeHighlighterImpl(this, startOffset, endOffset, layer, targetArea,
                                                                       textProperties, isPersistent);
    myHighlighters.add(segmentHighlighter);
    myCachedHighlighters = null;
    myHighlighterList.addSegmentHighlighter(segmentHighlighter);
    fireSegmentHighlighterChanged(segmentHighlighter);
    return segmentHighlighter;

  }

  public RangeHighlighter addRangeHighlighter(int startOffset,
                                              int endOffset,
                                              int layer,
                                              TextAttributes textProperties,
                                              HighlighterTargetArea targetArea) {
    return addRangeHighlighter(startOffset, endOffset, layer, textProperties, targetArea, false);
  }

  public void removeHighlighter(RangeHighlighter segmentHighlighter) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    boolean removed = myHighlighters.remove(segmentHighlighter);
    myCachedHighlighters = null;
    LOG.assertTrue(removed);
    myHighlighterList.removeSegmentHighlighter(segmentHighlighter);
    fireSegmentHighlighterChanged(segmentHighlighter);
  }

  public void removeAllHighlighters() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    for (int i = 0; i < myHighlighters.size(); i++) {
      RangeHighlighter highlighter = myHighlighters.get(i);
      fireSegmentHighlighterChanged(highlighter);
      myHighlighterList.removeSegmentHighlighter(highlighter);
    }
    myHighlighters.clear();
    myCachedHighlighters = null;
  }

  public Document getDocument() {
    return myDocument;
  }

  public void addMarkupModelListener(MarkupModelListener listener) {
    myListeners.add(listener);
    myCachedListeners = null;
  }

  public void removeMarkupModelListener(MarkupModelListener listener) {
    boolean success = myListeners.remove(listener);
    LOG.assertTrue(success);
    myCachedListeners = null;
  }

  private MarkupModelListener[] getCachedListeners() {
    if (myCachedListeners == null) {
      myCachedListeners = myListeners.toArray(new MarkupModelListener[myListeners.size()]);
    }

    return myCachedListeners;
  }

  protected void fireSegmentHighlighterChanged(RangeHighlighter segmentHighlighter) {
    MarkupModelEvent event = new MarkupModelEvent(this, segmentHighlighter);
    MarkupModelListener[] listeners = getCachedListeners();
    for (int i = 0; i < listeners.length; i++) {
      MarkupModelListener listener = listeners[i];
      listener.rangeHighlighterChanged(event);
    }
  }

  public HighlighterList getHighlighterList() {
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());
    return myHighlighterList;
  }
}
