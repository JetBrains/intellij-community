package com.intellij.psi.impl.source;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.CharTable;
import gnu.trove.TObjectIntHashMap;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 07.06.2004
 * Time: 15:37:59
 * To change this template use File | Settings | File Templates.
 */
public class CharTableImpl implements CharTable{
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.CharTableImpl");
  private final TObjectIntHashMap myEntries = new TObjectIntHashMap();
  private char[] myCurrentPage = null;
  private CharTableEntry[] myReferences = new CharTableEntry[0];
  private int currentReferencesEnd = 1;                                  
  private int bufferEnd = 0;

  public CharTableImpl() {
    bufferEnd = PAGE_SIZE;
  }

  public int getId(String str) {
    return getId(str.toCharArray(), 0, str.length());
  }

  public int checkId(String str) {
    return myEntries.get(str);
  }

  public int copyTo(int id, char[] buffer, int startOffset) {
    final CharTableEntry arrayElement = myReferences[id];
    final int length = arrayElement.length;
    System.arraycopy(arrayElement.buffer, arrayElement.offset, buffer, startOffset, length);
    return startOffset + length;
  }

  public int getId(char[] buffer, int offset, int length) {
    final int index = myEntries.get(StringFactory.createStringFromConstantArray(buffer, offset, length));
    if(index <= 0){
      final CharTableEntry entry = createEntry(buffer, offset, length);
      final int refsLength = myReferences.length;
      if(currentReferencesEnd >= refsLength - 1){
        final CharTableEntry[] oldReferences = myReferences;
        myReferences = new CharTableEntry[(int)(refsLength + Math.max(5, refsLength * 0.1))];
        System.arraycopy(oldReferences, 0, myReferences, 0, refsLength);
      }
      myEntries.put(entry, currentReferencesEnd);
      myReferences[currentReferencesEnd++ - 1] = entry;
      return currentReferencesEnd - 1;
    }
    return index;
  }

  private CharTableEntry createEntry(char[] buffer, int offset, int length) {
    if(length > PAGE_SIZE){
      // creating new page in case of long (>PAGE_SIZE) token
      final char[] page = new char[length];
      System.arraycopy(buffer, offset, page, 0, length);
      return new CharTableEntry(page, 0, length);
    }

    if(myCurrentPage == null || PAGE_SIZE - bufferEnd < length){
      // append to buffer
      myCurrentPage = new char[PAGE_SIZE];
      bufferEnd = 0;
    }
    System.arraycopy(buffer, offset, myCurrentPage, bufferEnd, length);
    bufferEnd += length;
    return new CharTableEntry(myCurrentPage, bufferEnd - length, length);
  }

  public Entry getEntry(int id) {
    checkId(id);
    return myReferences[id - 1];
  }

  public void checkId(int id) {
    if(id < 0 || id >= currentReferencesEnd){
      LOG.assertTrue(false);
    }
  }

  private static final class CharTableEntry implements Entry{
    char[] buffer;
    int offset;
    int length;

    public CharTableEntry(char[] buffer, int offset, int length) {
      this.buffer = buffer;
      this.offset = offset;
      this.length = length;
    }

    public int hashCode() {
      int off = offset;
      int h = 0;
      for (int i = 0; i < length; i++) {
          h = 31*h + buffer[off++];
      }
      return h;
    }

    public boolean equals(Object o) {
      if(o instanceof String){
        String str = (String)o;
        if(str.length() != length) return false;
        int off = offset;
        for (int i = 0; i < length; i++) {
            if(buffer[off++] != str.charAt(i)) return false;
        }
        return true;
      }
      else if(o instanceof CharTableEntry){
        final CharTableEntry entry = (CharTableEntry)o;
        return buffer == entry.buffer && offset == entry.offset && length == entry.length;
      }
      return false;
    }

    public char[] getBuffer() {
      return buffer;
    }

    public int getOffset() {
      return offset;
    }

    public int getLength() {
      return length;
    }

    public String toString() {
      return StringFactory.createStringFromConstantArray(buffer, offset, length);
    }
  }
}
