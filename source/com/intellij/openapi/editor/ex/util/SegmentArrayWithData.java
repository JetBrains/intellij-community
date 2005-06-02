package com.intellij.openapi.editor.ex.util;

public class SegmentArrayWithData extends SegmentArray {
  private short[] myData;

  public SegmentArrayWithData() {
    myData = new short[INITIAL_SIZE];
  }

  public void setElementAt(int i, int startOffset, int endOffset, int data) {
    if (data < 0 && data > Short.MAX_VALUE) throw new IndexOutOfBoundsException("data out of short range" + data);
    super.setElementAt(i, startOffset, endOffset);
    myData = relocateArray(myData, i+1);
    myData[i] = (short)data;
  }

  public void remove(int startIndex, int endIndex) {
    myData = remove(myData, startIndex, endIndex);
    super.remove(startIndex, endIndex);
  }

  public void insert(SegmentArrayWithData segmentArray, int startIndex) {
    myData = insert(myData, segmentArray.myData, startIndex, segmentArray.getSegmentCount());
    super.insert(segmentArray, startIndex);
  }

  public short getSegmentData(int index) {
    if(index < 0 || index >= mySegmentCount) {
      throw new IndexOutOfBoundsException("Wrong index: " + index);
    }
    return myData[index];
  }

  public void setSegmentData(int index, int data) {
    if(index < 0 || index >= mySegmentCount) throw new IndexOutOfBoundsException("Wrong index: " + index);
    if (data < 0 && data > Short.MAX_VALUE) throw new IndexOutOfBoundsException("data out of short range" + data);
    myData[index] = (short)data;
  }
}

