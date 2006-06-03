package com.intellij.openapi.editor.ex.util;

import com.intellij.openapi.diagnostic.Logger;

public class SegmentArray {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.ex.util.SegmentArray");
  protected SegmentArray() {
    myStarts = new int[INITIAL_SIZE];
    myEnds = new int[INITIAL_SIZE];
  }

  protected void setElementAt(int i, int startOffset, int endOffset) {
    if (startOffset < 0) {
      LOG.error("Invalid startOffset:" + startOffset);
    }
    if (endOffset < 0) {
      LOG.error("Invalid endOffset:" + endOffset);
    }

    if(i >= mySegmentCount) {
      mySegmentCount = i+1;
    }
    myStarts = relocateArray(myStarts, i);
    myStarts[i] = startOffset;

    myEnds = relocateArray(myEnds, i);
    myEnds[i] = endOffset;
  }

  protected static int[] relocateArray(int[] array, int index) {
    if(index < array.length)
      return array;

    int newArraySize = array.length;
    if(newArraySize == 0) {
      newArraySize = 16;
    }
    while(newArraySize <= index) {
      newArraySize = (newArraySize * 120) / 100;
    }
    int[] newArray = new int[newArraySize];
    System.arraycopy(array, 0, newArray, 0, array.length);
    return newArray;
  }
  protected static short[] relocateArray(short[] array, int index) {
    if(index < array.length)
      return array;

    int newArraySize = array.length;
    if(newArraySize == 0) {
      newArraySize = 16;
    }
    while(newArraySize <= index) {
      newArraySize = (newArraySize * 120) / 100;
    }
    short[] newArray = new short[newArraySize];
    System.arraycopy(array, 0, newArray, 0, array.length);
    return newArray;
  }

  public final int findSegmentIndex(int offset) {
    if(mySegmentCount <= 0) {
      if (offset == 0) return 0;
      throw new IllegalStateException("no segments avaliable");
    }

    if(offset > myEnds[mySegmentCount -1] || offset < 0){
      throw new IndexOutOfBoundsException("Wrong offset: " + offset);
    }

    if (offset == myEnds[mySegmentCount - 1]) return mySegmentCount - 1;

    int start = 0;
    int end = mySegmentCount - 1;

    while(start < end) {
      int i = (start + end)/2;
      if(offset < myStarts[i]) {
        end = i-1;
      } else if(offset >= myEnds[i]) {
        start = i+1;
      } else {
        return i;
      }
    }

    assert (offset >= myStarts[start] && offset < myEnds[start]);

    return start;
  }

  public final void changeSegmentLength(int startIndex, int change) {
    if(startIndex >= 0 && startIndex < mySegmentCount) {
      myEnds[startIndex] += change;
    }
    shiftSegments(startIndex+1, change);
  }

  public final void shiftSegments(int startIndex, int shift) {
    for(int i=startIndex; i<mySegmentCount; i++) {
      myStarts[i] += shift;
      myEnds[i] += shift;
      if (myStarts[i] < 0 || myEnds[i] < 0) {
        LOG.error("Error shifting segments: myStarts[" + i + "] = " + myStarts[i] + ", myEnds[" + i + "] = " + myEnds[i]);
      }
    }
  }

  public void removeAll() {
    mySegmentCount = 0;
  }

  public void remove(int startIndex, int endIndex) {
    myStarts = remove(myStarts, startIndex, endIndex);
    myEnds = remove(myEnds, startIndex, endIndex);
    mySegmentCount -= endIndex - startIndex;
  }

  protected int[] remove(int[] array, int startIndex, int endIndex) {
    if(endIndex < mySegmentCount) {
      System.arraycopy(array, endIndex, array, startIndex, mySegmentCount-endIndex);
    }
    return array;
  }
  protected short[] remove(short[] array, int startIndex, int endIndex) {
    if(endIndex < mySegmentCount) {
      System.arraycopy(array, endIndex, array, startIndex, mySegmentCount-endIndex);
    }
    return array;
  }
  protected long[] remove(long[] array, int startIndex, int endIndex) {
    if(endIndex < mySegmentCount) {
      System.arraycopy(array, endIndex, array, startIndex, mySegmentCount-endIndex);
    }
    return array;
  }

  protected void insert(SegmentArray segmentArray, int startIndex) {
    myStarts = insert(myStarts, segmentArray.myStarts, startIndex, segmentArray.getSegmentCount());
    myEnds = insert(myEnds, segmentArray.myEnds, startIndex, segmentArray.getSegmentCount());
    mySegmentCount += segmentArray.getSegmentCount();
  }

  protected int[] insert(int[] array, int[] insertArray, int startIndex, int insertLength) {
    int[] newArray = relocateArray(array, mySegmentCount + insertLength);
    if(startIndex < mySegmentCount) {
      System.arraycopy(newArray, startIndex, newArray, startIndex+insertLength, mySegmentCount-startIndex);
    }
    System.arraycopy(insertArray, 0, newArray, startIndex, insertLength);
    return newArray;
  }

  protected short[] insert(short[] array, short[] insertArray, int startIndex, int insertLength) {
    short[] newArray = relocateArray(array, mySegmentCount + insertLength);
    if(startIndex < mySegmentCount) {
      System.arraycopy(newArray, startIndex, newArray, startIndex+insertLength, mySegmentCount-startIndex);
    }
    System.arraycopy(insertArray, 0, newArray, startIndex, insertLength);
    return newArray;
  }

  public int getSegmentStart(int index) {
    if(index < 0 || index >= mySegmentCount) {
      throw new IndexOutOfBoundsException("Wrong line: " + index);
    }
    return myStarts[index];
  }

  public int getSegmentEnd(int index) {
    if(index < 0 || index >= mySegmentCount) {
      throw new IndexOutOfBoundsException("Wrong line: " + index);
    }
    return myEnds[index];
  }


  public int getSegmentCount() {
    return mySegmentCount;
  }

  private int[] myStarts;
  private int[] myEnds;

  protected int mySegmentCount = 0;
  protected final static int INITIAL_SIZE = 64;
}

