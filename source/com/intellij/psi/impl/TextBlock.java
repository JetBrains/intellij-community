package com.intellij.psi.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;

public class TextBlock extends DocumentAdapter {
  private Document myDocument; // Will hold a document on a hard reference until there's uncommited PSI for this document.

  public TextBlock(final Document document) {
    myDocument = document;
  }

  private int myStartOffset = -1;
  private int myTextEndOffset = -1;
  private int myPsiEndOffset = -1;
  private boolean myIsLocked = false;

  public boolean isEmpty() {
    return myStartOffset == -1;
  }

  public void clear() {
    myStartOffset = -1;
  }

  public int getStartOffset() {
    return myStartOffset;
  }

  public int getTextEndOffset() {
    return myTextEndOffset;
  }

  public void lock() {
    myIsLocked = true;
  }

  public void unlock() {
    myIsLocked = false;
  }

  public boolean isLocked() {
    return myIsLocked;
  }

  public int getPsiEndOffset() {
    return myPsiEndOffset;
  }

  public void documentChanged(DocumentEvent e) {
    assert myDocument == e.getDocument();
    assert !myIsLocked;

    final int offset = e.getOffset();
    if (isEmpty()) {
      myStartOffset = offset;
      myTextEndOffset = offset + e.getNewLength();
      myPsiEndOffset = offset + e.getOldLength();
    }
    else {
      int shift = offset + e.getOldLength() - myTextEndOffset;
      if (shift > 0) {
        myPsiEndOffset += shift;
        myTextEndOffset = offset + e.getNewLength();
      }
      else {
        int sizeDelta = e.getNewLength() - e.getOldLength();
        myTextEndOffset += sizeDelta;
      }

      myStartOffset = Math.min(myStartOffset, offset);
    }
  }
}
