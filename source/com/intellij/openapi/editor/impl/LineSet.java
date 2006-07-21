package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.LineIterator;
import com.intellij.openapi.editor.ex.util.SegmentArrayWithData;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.util.text.MergingCharSequence;

/**
 *
 */
public class LineSet{
  private SegmentArrayWithData mySegments = new SegmentArrayWithData();
  private static final int MODIFIED_MASK = 0x4;
  private static final int SEPARATOR_MASK = 0x3;

  public int findLineIndex(int offset) {
    return mySegments.findSegmentIndex(offset);
  }

  public LineIterator createIterator() {
    return new LineIteratorImpl(this);
  }

  final int getLineStart(int index) {
    return mySegments.getSegmentStart(index);
  }

  final int getLineEnd(int index) {
    return mySegments.getSegmentEnd(index);
  }

  final boolean isModified(int index) {
    return (mySegments.getSegmentData(index) & MODIFIED_MASK) != 0;
  }

  final int getSeparatorLength(int index) {
    return (int) (mySegments.getSegmentData(index) & SEPARATOR_MASK);
  }

  final int getLineCount() {
    return mySegments.getSegmentCount();
  }

  public void documentCreated(DocumentEvent e) {
    initSegments(e.getDocument().getCharsSequence(), false);
  }

  public void changedUpdate(DocumentEvent e1) {
    DocumentEventImpl e = (DocumentEventImpl) e1;
    if (e.isOnlyOneLineChanged() && mySegments.getSegmentCount() > 0) {
      processOneLineChange(e);
    } else {
      if (mySegments.getSegmentCount() == 0 || e.getStartOldIndex() >= mySegments.getSegmentCount() ||
          e.getStartOldIndex() < 0) {
        initSegments(e.getDocument().getCharsSequence(), true);
        return;
      }

      processMultilineChange(e);
    }

    if (e.isWholeTextReplaced()) {
      clearModificationFlags();
    }
  }

  private void processMultilineChange(DocumentEventImpl e) {
    int offset = e.getOffset();
    CharSequence newString = e.getNewFragment();
    CharSequence chars = e.getDocument().getCharsSequence();

    int oldStartLine = e.getStartOldIndex();
    int offset1 = getLineStart(oldStartLine);
    if (offset1 != offset) {
      CharSequence prefix = chars.subSequence(offset1, offset);
      newString = new MergingCharSequence(prefix, newString);
    }

    int oldEndLine = findLineIndex(e.getOffset() + e.getOldLength());
    if (oldEndLine < 0) {
      oldEndLine = getLineCount() - 1;
    }
    int offset2 = getLineEnd(oldEndLine);
    if (offset2 != offset + e.getOldLength()) {
      final int start = offset + e.getNewLength();
      final int length = offset2 - offset - e.getOldLength();
      CharSequence postfix = chars.subSequence(start, start + length);
      newString = new MergingCharSequence(newString, postfix);
    }

    updateSegments(newString, oldStartLine, oldEndLine, offset1, e);
    // We add empty line at the end, if the last line ends by line separator.
    addEmptyLineAtEnd();
  }

  private void updateSegments(CharSequence newText, int oldStartLine, int oldEndLine, int offset1,
                                              DocumentEventImpl e) {
    int count = 0;
    LineTokenizer lineTokenizer = new LineTokenizer(newText);
    for (int index = oldStartLine; index <= oldEndLine; index++) {
      if (!lineTokenizer.atEnd()) {
        setSegmentAt(mySegments, index, lineTokenizer, offset1, true);
        lineTokenizer.advance();
      } else {
        mySegments.remove(index, oldEndLine + 1);
        break;
      }
      count++;
    }
    if (!lineTokenizer.atEnd()) {
      SegmentArrayWithData insertSegments = new SegmentArrayWithData();
      int i = 0;
      while (!lineTokenizer.atEnd()) {
        setSegmentAt(insertSegments, i, lineTokenizer, offset1, true);
        lineTokenizer.advance();
        count++;
        i++;
      }
      mySegments.insert(insertSegments, oldEndLine + 1);
    }
    int shift = e.getNewLength() - e.getOldLength();
    mySegments.shiftSegments(oldStartLine + count, shift);
  }

  private void processOneLineChange(DocumentEventImpl e) {
    // Check, if the change on the end of text
    if (e.getOffset() >= mySegments.getSegmentEnd(mySegments.getSegmentCount() - 1)) {
      mySegments.changeSegmentLength(mySegments.getSegmentCount() - 1, e.getNewLength() - e.getOldLength());
      setSegmentModified(mySegments, mySegments.getSegmentCount() - 1);
    } else {
      mySegments.changeSegmentLength(e.getStartOldIndex(), e.getNewLength() - e.getOldLength());
      setSegmentModified(mySegments, e.getStartOldIndex());
    }
  }

  public void clearModificationFlags() {
    for (int i = 0; i < mySegments.getSegmentCount(); i++) {
      mySegments.setSegmentData(i, mySegments.getSegmentData(i) & ~MODIFIED_MASK);
    }
  }

  private static void setSegmentAt(SegmentArrayWithData segmentArrayWithData, int index, LineTokenizer lineTokenizer, int offsetShift, boolean isModified) {
    int offset = lineTokenizer.getOffset() + offsetShift;
    int length = lineTokenizer.getLength();
    int separatorLength = lineTokenizer.getLineSeparatorLength();
    int separatorAndModifiedFlag = separatorLength;
    if(isModified) {
      separatorAndModifiedFlag |= MODIFIED_MASK;
    }
    segmentArrayWithData.setElementAt(index, offset, offset + length + separatorLength, separatorAndModifiedFlag);
  }

  private static void setSegmentModified(SegmentArrayWithData segments, int i) {
    segments.setSegmentData(i, segments.getSegmentData(i)|MODIFIED_MASK);
  }

  private void initSegments(CharSequence text, boolean toSetModified) {
    mySegments.removeAll();
    LineTokenizer lineTokenizer = new LineTokenizer(text);
    int i = 0;
    while(!lineTokenizer.atEnd()) {
      setSegmentAt(mySegments, i, lineTokenizer, 0, toSetModified);
      i++;
      lineTokenizer.advance();
    }
    // We add empty line at the end, if the last line ends by line separator.
    addEmptyLineAtEnd();
  }

  // Add empty line at the end, if the last line ends by line separator.
  private void addEmptyLineAtEnd() {
    int segmentCount = mySegments.getSegmentCount();
    if(segmentCount > 0 && getSeparatorLength(segmentCount-1) > 0) {
      mySegments.setElementAt(segmentCount, mySegments.getSegmentEnd(segmentCount-1),  mySegments.getSegmentEnd(segmentCount-1), 0);
      setSegmentModified(mySegments, segmentCount);
    }
  }

}
