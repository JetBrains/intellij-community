package com.intellij.psi.impl.source;

import com.intellij.util.CharTable;
import com.intellij.util.PatchedWeakReference;
import com.intellij.util.text.CharArrayUtil;
import gnu.trove.THashMap;

import java.lang.ref.WeakReference;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 07.06.2004
 * Time: 15:37:59
 * To change this template use File | Settings | File Templates.
 */
public class CharTableImpl implements CharTable {
  private final THashMap<Object, WeakReference<? extends CharSequence>> myEntries =
    new THashMap<Object, WeakReference<? extends CharSequence>>(new CharTableEntryHashingStrategy()){
      protected void rehash(final int newCapacity) {
        for (int i = 0; i < _set.length; i++) {
          final Object o = _set[i];
          if(o == REMOVED) continue;
          final WeakReference<? extends CharSequence> reference = (WeakReference<? extends CharSequence>)o;
          if(reference != null && reference.get() == null) _set[i] = REMOVED;
        }
        super.rehash(newCapacity);
      }
    };
  private char[] myCurrentPage = null;
  private int bufferEnd = 0;

  public CharTableImpl() {
    bufferEnd = PAGE_SIZE;
  }

  public CharSequence intern(final CharSequence text) {
    WeakReference<? extends CharSequence> weakReference = myEntries.get(text);
    CharSequence entry = weakReference != null ? weakReference.get() : null;
    if (entry == null ) {
      entry = createEntry(text);
      weakReference = new PatchedWeakReference(entry);
      myEntries.put(weakReference, weakReference);
    }
    return entry;
  }

  private CharTableEntry createEntry(CharSequence text) {
    final int length = text.length();
    if (length > PAGE_SIZE) {
      // creating new page in case of long (>PAGE_SIZE) token
      final char[] page = new char[length];
      CharArrayUtil.getChars(text, page, 0);
      return new CharTableEntry(page, 0, length);
    }

    if (myCurrentPage == null || PAGE_SIZE - bufferEnd < length) {
      // append to buffer
      myCurrentPage = new char[PAGE_SIZE];
      bufferEnd = 0;
    }
    CharArrayUtil.getChars(text, myCurrentPage, bufferEnd);
    bufferEnd += length;
    return new CharTableEntry(myCurrentPage, bufferEnd - length, length);
  }

  private static final class CharTableEntry implements CharSequence {
    char[] buffer;
    int offset;
    int length;
    int hashCode;

    public CharTableEntry(char[] buffer, int offset, int length) {
      if (length < 0 || offset + length > buffer.length) throw new IndexOutOfBoundsException("");
      this.buffer = buffer;
      this.offset = offset;
      this.length = length;
    }

    public int length() {
      return length;
    }

    public char charAt(int index) {
      return buffer[index + offset];
    }

    public CharSequence subSequence(int start, int end) {
      return new CharTableEntry(buffer, offset + start, offset + end);
    }

    public String toString() {
      return StringFactory.createStringFromConstantArray(buffer, offset, length);
    }
  }
}
