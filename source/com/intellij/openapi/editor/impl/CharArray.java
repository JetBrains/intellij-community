package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.text.CharArrayCharSequence;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.CharSequenceBackedByArray;

import java.lang.ref.SoftReference;

/**
 * @author cdr
 */
abstract class CharArray implements CharSequenceBackedByArray {
  protected int myCount = 0;
  private CharSequence myOriginalSequence;
  private char[] myArray = null;
  private SoftReference<String> myStringRef = null; // buffers String value - for not to generate it every time

  public CharArray(CharSequence chars) {
    myOriginalSequence = chars;
    myCount = chars.length();
  }

  public CharArray() {
    this("");
  }

  protected abstract DocumentEvent beforeChangedUpdate(int offset, CharSequence oldString, CharSequence newString);
  protected abstract void afterChangedUpdate(DocumentEvent event, long newModificationStamp);

  public void replaceText(CharSequence chars) {
    myOriginalSequence = chars;
    myArray = null;
    myCount = chars.length();
    myStringRef = null;
  }

  public void replace(int startOffset, int endOffset, CharSequence toDelete, CharSequence newString, long newModificationStamp) {
    final DocumentEvent event = beforeChangedUpdate(startOffset, toDelete, newString);
    doRemove(startOffset,endOffset);
    doInsert(newString, startOffset);
    afterChangedUpdate(event, newModificationStamp);
  }

  public void remove(int startIndex, int endIndex, CharSequence toDelete) {
    DocumentEvent event = beforeChangedUpdate(startIndex, toDelete, null);
    doRemove(startIndex, endIndex);
    afterChangedUpdate(event, LocalTimeCounter.currentTime());
  }

  private void doRemove(final int startIndex, final int endIndex) {
    if (startIndex == endIndex) {
      return;
    }
    prepareForModification();

    if (endIndex < myCount) {
      System.arraycopy(myArray, endIndex, myArray, startIndex, myCount - endIndex);
    }
    myCount -= endIndex - startIndex;
  }

  public void insert(CharSequence s, int startIndex) {
    DocumentEvent event = beforeChangedUpdate(startIndex, null, s);
    doInsert(s, startIndex);

    afterChangedUpdate(event, LocalTimeCounter.currentTime());
  }

  private void doInsert(final CharSequence s, final int startIndex) {
    prepareForModification();

    int insertLength = s.length();
    myArray = relocateArray(myArray, myCount + insertLength);
    if (startIndex < myCount) {
      System.arraycopy(myArray, startIndex, myArray, startIndex + insertLength, myCount - startIndex);
    }
    
    if (s instanceof String) {
      ((String)s).getChars(0,insertLength,myArray,startIndex);
    } else {
      CharArrayUtil.getChars(s, myArray,startIndex);
    }
    myCount += insertLength;
  }

  private void prepareForModification() {
    if (myOriginalSequence != null) {
      myArray = new char[myOriginalSequence.length()];
      CharArrayUtil.getChars(myOriginalSequence, myArray, 0);
      myOriginalSequence = null;
    }
    myStringRef = null;
  }

  public CharSequence getCharArray() {
    if (myOriginalSequence != null) return myOriginalSequence;
    return this;
  }

  public String toString() {
    String str = myStringRef != null ? myStringRef.get() : null;
    if (str == null) {
      if (myOriginalSequence != null) {
        str = myOriginalSequence.toString();
      }
      else {
        str = new String(myArray, 0, myCount);
      }
      myStringRef = new SoftReference<String>(str);
    }
    return str;
  }

  public final int length() {
    return myCount;
  }

  public final char charAt(int i) {
    if (i < 0 || i >= myCount) {
      throw new IndexOutOfBoundsException("Wrong offset: " + i);
    }
    if (myOriginalSequence != null) return myOriginalSequence.charAt(i);
    return myArray[i];
  }

  public CharSequence subSequence(int start, int end) {
    if (start == 0 && end == myCount) return this;
    if (myOriginalSequence != null) {
      return myOriginalSequence.subSequence(start, end);
    }
    return new CharArrayCharSequence(myArray, start, end);
  }

  public char[] getChars() {
    if (myOriginalSequence != null) return CharArrayUtil.fromSequence(myOriginalSequence);
    return myArray;
  }

  public void getChars(final char[] dst, final int dstOffset) {
    System.arraycopy(myArray, 0, dst, dstOffset, length());
  }

  public CharSequence substring(int start, int end) {
    if (start == end) return "";
    if (myOriginalSequence != null) {
      return myOriginalSequence.subSequence(start, end);
    }
    return new String(myArray, start, end - start);
  }

  private static char[] relocateArray(char[] array, int index) {
    if (index < array.length) {
      return array;
    }

    int newArraySize = array.length;
    if (newArraySize == 0) {
      newArraySize = 16;
    }
    while (newArraySize <= index) {
      newArraySize = (newArraySize * 120) / 100 + 1;
    }
    char[] newArray = new char[newArraySize];
    System.arraycopy(array, 0, newArray, 0, array.length);
    return newArray;
  }
}
