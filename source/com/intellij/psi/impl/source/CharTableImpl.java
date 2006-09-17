package com.intellij.psi.impl.source;

import com.intellij.util.CharTable;
import com.intellij.util.text.CharSequenceHashingStrategy;
import gnu.trove.THashSet;

/**
 * @author max
 */
public class CharTableImpl extends THashSet<CharSequence> implements CharTable {
  private final static int INTERN_THRESHOLD = 40; // 40 or more characters long tokens won't be interned.
  private final static CharSequenceHashingStrategy HASHER = new CharSequenceHashingStrategy();

  //private char[] myCurrentPage = null;
  //private int bufferEnd = 0;

  public CharTableImpl() {
    super(10, 0.9f, HASHER);

    //bufferEnd = PAGE_SIZE;
  }

  public synchronized CharSequence intern(final CharSequence text) {
    if (text.length() > INTERN_THRESHOLD) return text.toString();

    int idx = index(text);
    if (idx >= 0) {
      //noinspection NonPrivateFieldAccessedInSynchronizedContext
      return (CharSequence)_set[idx];
    }

    final CharSequence entry = text.toString();
    boolean added = add(entry);
    assert added;

    return entry;
  }

  //private CharSequence createEntry(CharSequence text) {
  //  final int length = text.length();
  //  if (length > PAGE_SIZE) {
  //    // creating new page in case of long (>PAGE_SIZE) token
  //    final char[] page = new char[length];
  //    CharArrayUtil.getChars(text, page, 0);
  //    return new CharTableEntry(page, 0, length);
  //  }
  //
  //  if (myCurrentPage == null || PAGE_SIZE - bufferEnd < length) {
  //    // append to buffer
  //    myCurrentPage = new char[PAGE_SIZE];
  //    bufferEnd = 0;
  //  }
  //  CharArrayUtil.getChars(text, myCurrentPage, bufferEnd);
  //  bufferEnd += length;
  //  return new CharTableEntry(myCurrentPage, bufferEnd - length, length);
  //}
  //
  //private static final class CharTableEntry implements CharSequenceWithStringHash {
  //  char[] buffer;
  //  int offset;
  //  int length;
  //  int hashCode;
  //
  //  public CharTableEntry(char[] buffer, int offset, int length) {
  //    if (length < 0 || offset + length > buffer.length) throw new IndexOutOfBoundsException("");
  //    this.buffer = buffer;
  //    this.offset = offset;
  //    this.length = length;
  //    hashCode = StringUtil.stringHashCode(buffer, offset, length);
  //  }
  //
  //  public int length() {
  //    return length;
  //  }
  //
  //  public char charAt(int index) {
  //    return buffer[index + offset];
  //  }
  //
  //  public CharSequence subSequence(int start, int end) {
  //    return new CharTableEntry(buffer, offset + start, offset + end);
  //  }
  //
  //  public String toString() {
  //    return StringFactory.createStringFromConstantArray(buffer, offset, length);
  //  }
  //
  //  public int hashCode() {
  //    return hashCode;
  //  }
  //}
}
