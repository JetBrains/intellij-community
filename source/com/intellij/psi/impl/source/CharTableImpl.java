package com.intellij.psi.impl.source;

import com.intellij.openapi.util.text.CharSequenceWithStringHash;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.CharTable;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.CharSequenceHashingStrategy;
import org.apache.commons.collections.map.ReferenceMap;

import java.lang.ref.Reference;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 07.06.2004
 * Time: 15:37:59
 * To change this template use File | Settings | File Templates.
 */
public class CharTableImpl implements CharTable {
  private final static CharSequenceHashingStrategy HASHER = new CharSequenceHashingStrategy();
  private final Map<CharSequence,CharSequence> myEntries = new WeakCharEntryMap();

  private char[] myCurrentPage = null;
  private int bufferEnd = 0;

  public CharTableImpl() {
    bufferEnd = PAGE_SIZE;
  }

  public synchronized CharSequence intern(final CharSequence text) {
    CharSequence entry = myEntries.get(text);
    if (entry == null ) {
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

  private static final class CharTableEntry implements CharSequenceWithStringHash {
    char[] buffer;
    int offset;
    int length;
    int hashCode;

    public CharTableEntry(char[] buffer, int offset, int length) {
      if (length < 0 || offset + length > buffer.length) throw new IndexOutOfBoundsException("");
      this.buffer = buffer;
      this.offset = offset;
      this.length = length;
      hashCode = StringUtil.stringHashCode(buffer, offset, length);
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

    public int hashCode() {
      return hashCode;
    }
  }

  private static class WeakCharEntryMap extends ReferenceMap {
    public WeakCharEntryMap() {
      super(ReferenceMap.WEAK, ReferenceMap.WEAK, true);
    }

    protected int hash(Object key) {
      return HASHER.computeHashCode((CharSequence)key);
    }

    protected int hashEntry(Object key, Object value) {
      return HASHER.computeHashCode((CharSequence)key);
    }

    protected boolean isEqualKey(Object key1, Object key2) {
      return HASHER.equals((CharSequence)key1, (CharSequence)((Reference)key2).get());
    }
  }
}
