package com.intellij.openapi.editor.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.util.Key;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

public class RangeMarkerImpl extends DocumentAdapter implements RangeMarker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.RangeMarkerImpl");

  protected Document myDocument;
  protected int myStart;
  protected int myEnd;
  private boolean isValid = true;
  private boolean isExpandToLeft = false;
  private boolean isExpandToRight = false;

  private static long counter = 0;
  private long myId;

  private THashMap myUserMap = null;

  public RangeMarkerImpl(Document document, int start, int end) {
    if (!(start <= end && start >= 0 && end <= document.getTextLength())) {
      if (start < 0) {
        LOG.error("Wrong start: " + start);
      }
      else if (end > document.getTextLength()) {
        LOG.error("Wrong end: " + end, "document length="+document.getTextLength());
      }
      else {
        LOG.error("start > end: start=" + start+"; end="+end);
      }
    }

    myDocument = document;
    myStart = start;
    myEnd = end;
    myId = counter;
    counter++;
    registerInDocument();
  }

  protected void registerInDocument() {
    ((DocumentImpl)myDocument).addRangeMarker(this);
  }

  public long getId() {
    return myId;
  }

  public int getStartOffset() {
    return myStart;
  }

  public int getEndOffset() {
    return myEnd;
  }

  public boolean isValid() {
    return isValid;
  }

  public void invalidate() {
    isValid = false;
  }

  @NotNull
  public Document getDocument() {
    return myDocument;
  }

  public void setGreedyToLeft(boolean greedy) {
    isExpandToLeft = greedy;
  }

  public void setGreedyToRight(boolean greedy) {
    isExpandToRight = greedy;
  }

  public void documentChanged(DocumentEvent e) {
    int oldStart = myStart;
    int oldEnd = myEnd;
    changedUpdateImpl(e);
    if (isValid && (myStart > myEnd || myStart < 0 || myEnd > myDocument.getTextLength())) {
      LOG.error("RangeMarker[" + oldStart + ", " + oldEnd + "] update failed. Event = " + e + ". Result[" + myStart + ", " + myEnd + "], doc length=" + myDocument.getTextLength());
      isValid = false;
    }
  }

  private void changedUpdateImpl(DocumentEvent e) {
    if (!isValid) return;

    // Process if one point.
    if (myStart == myEnd && !isExpandToRight && !isExpandToLeft) {
      processIfOnePoint(e);
      return;
    }

    final int offset = e.getOffset();
    final int oldLength = e.getOldLength();
    final int newLength = e.getNewLength();

    // changes after the end.
    if (myEnd < offset || (!isExpandToRight && myEnd == offset)) {
      return;
    }

    // changes before start
    if (myStart > offset + oldLength || (!isExpandToLeft && myStart == offset + oldLength)) {
      myStart += newLength - oldLength;
      myEnd += newLength - oldLength;
      return;
    }

    // Changes inside marker's area. Expand/collapse.
    if (myStart <= offset && myEnd >= offset + oldLength) {
      myEnd += newLength - oldLength;
      return;
    }

    // At this point we either have (myStart xor myEnd inside changed area) or whole area changed.

    // Replacing prefix or suffix...
    if (myStart >= offset && myStart <= offset + oldLength && myEnd > offset + oldLength) {
      myEnd += newLength - oldLength;
      myStart = offset + newLength;
      return;
    }

    if (myEnd >= offset && myEnd <= offset + oldLength && myStart < offset) {
      myEnd = offset;
      return;
    }

    invalidate();
  }

  private void processIfOnePoint(DocumentEvent e) {
    int offset = e.getOffset();
    int oldLength = e.getOldLength();
    int oldEnd = offset + oldLength;
    if (myStart > offset && myStart < oldEnd) {
      invalidate();
      return;
    }
    if (myStart > oldEnd || myStart == oldEnd  && oldLength > 0) {
      myStart += e.getNewLength() - oldLength;
      myEnd += e.getNewLength() - oldLength;
    }
  }

  public <T> T getUserData(Key<T> key){
    synchronized(this){
      if (myUserMap == null) return null;
      return (T)myUserMap.get(key);
    }
  }

  public <T> void putUserData(Key<T> key, T value){
    synchronized(this){
      if (myUserMap == null){
        if (value == null) return;
        myUserMap = new THashMap();
      }
      if (value != null){
        myUserMap.put(key, value);
      }
      else{
        myUserMap.remove(key);
        if (myUserMap.isEmpty()){
          myUserMap = null;
        }
      }
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "RangeMarker[" + (isValid ? "valid" : "invalid") + "," + myStart + "," + myEnd + "]";
  }
}