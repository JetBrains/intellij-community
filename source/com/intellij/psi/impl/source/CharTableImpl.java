package com.intellij.psi.impl.source;

import com.intellij.util.CharTable;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.CharSequenceHashingStrategy;
import gnu.trove.THashMap;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 07.06.2004
 * Time: 15:37:59
 * To change this template use File | Settings | File Templates.
 */
public class CharTableImpl implements CharTable {
  private final THashMap<CharSequence, CharSequence> myEntries =
    new THashMap<CharSequence, CharSequence>(new CharSequenceHashingStrategy());
  private char[] myCurrentPage = null;
  private int bufferEnd = 0;

  public CharTableImpl() {
    bufferEnd = PAGE_SIZE;
  }

  public CharSequence intern(final CharSequence text) {
    CharSequence entry = myEntries.get(text);
    if (entry == null) {
      entry = createEntry(text);
      myEntries.put(entry, entry);
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
